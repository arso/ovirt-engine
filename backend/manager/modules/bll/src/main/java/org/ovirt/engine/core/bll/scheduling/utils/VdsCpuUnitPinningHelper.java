package org.ovirt.engine.core.bll.scheduling.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.ovirt.engine.core.common.businessentities.CpuPinningPolicy;
import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VdsCpuUnit;
import org.ovirt.engine.core.common.utils.CpuPinningHelper;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.vdsbroker.ResourceManager;

public class VdsCpuUnitPinningHelper {
    @Inject
    private ResourceManager resourceManager;

    /**
     * This function will tell if the host is capable to run a given dedicated CPU policy VM.
     *
     * The function apply the pending CPU pinning on the host topology, then:
     * Pass on the online sockets for the host, check its available cores and count them as long we don't break
     * the virtual topology.
     * If there are enough pCPUs - return true, otherwise false.
     *
     * @param vmToPendingPinnings Map of VDS GUID keys to list of VdsCpuUnits pending to be taken.
     * @param vm VM object.
     * @param hostId GUID of the VDS object.
     * @return boolean. True if possible to dedicate the VM on the host. Otherwise false.
     */
    public boolean isDedicatedCpuPinningPossibleAtHost(Map<Guid, List<VdsCpuUnit>> vmToPendingPinnings,
            VM vm, Guid hostId) {
        List<VdsCpuUnit> cpuTopology = resourceManager.getVdsManager(hostId).getCpuTopology();

        if (vm.getCpuPinningPolicy() != CpuPinningPolicy.DEDICATED) {
            // TODO: Implementation for siblings and isolate threads
            return false;
        }

        previewPinOfPendingExclusiveCpus(cpuTopology, vmToPendingPinnings, vm.getCpuPinningPolicy());

        int socketsLeft = vm.getNumOfSockets();

        for (int socket : getOnlineSockets(cpuTopology)) {
            int coresInSocket = getAvailableCores(cpuTopology, socket, vm.getThreadsPerCpu());
            socketsLeft -= coresInSocket / vm.getCpuPerSocket();
            if (socketsLeft <= 0) {
                return true;
            }
        }
        return false;
    }

    private void previewPinOfPendingExclusiveCpus(List<VdsCpuUnit> cpuTopology, Map<Guid, List<VdsCpuUnit>> vmToPendingPinning, CpuPinningPolicy cpuPinningPolicy) {
        for (var vmToPendingPinningEntry : vmToPendingPinning.entrySet()) {
            vmToPendingPinningEntry.getValue().forEach(vdsCpuUnit -> {
                VdsCpuUnit cpuUnit = getCpu(cpuTopology, vdsCpuUnit.getCpu());
                cpuUnit.pinVm(vmToPendingPinningEntry.getKey(), cpuPinningPolicy);
            });
        }
    }

    /**
     * This function will allocate the host CPUs to the given CPU pinning policy.
     *
     * The function apply the pending CPU pinning on the host topology, then:
     * The function will select the most available socket (most free CPUs). It will allocate the CPUs and check if we
     * pass the virtual topology.
     * When there are no more vCPUs to allocate - return the list of VdsCpuUnit we chose.
     *
     * @param vm VM object.
     * @param vmToPendingPinnings Map of VDS GUID keys to list of VdsCpuUnits pending to be taken.
     * @param hostId GUID of the VDS object.
     * @return List<{@link VdsCpuUnit}>. The list of VdsCpuUnit we are going to use. If not possible, return null.
     */
    public List<VdsCpuUnit> updatePhysicalCpuAllocations(VM vm, Map<Guid, List<VdsCpuUnit>> vmToPendingPinnings, Guid hostId) {
        if (vm.getCpuPinningPolicy() == CpuPinningPolicy.NONE) {
            return new ArrayList<>();
        }
        List<VdsCpuUnit> cpuTopology = resourceManager.getVdsManager(hostId).getCpuTopology();
        if (cpuTopology.isEmpty()) {
            return new ArrayList<>();
        }

        previewPinOfPendingExclusiveCpus(cpuTopology, vmToPendingPinnings, vm.getCpuPinningPolicy());

        if (vm.getCpuPinningPolicy() != CpuPinningPolicy.DEDICATED) {
            List<VdsCpuUnit> cpusToBeAllocated = new ArrayList<>();
            String cpuPinning = vm.getCpuPinningPolicy() == CpuPinningPolicy.MANUAL ? vm.getCpuPinning() : vm.getCurrentCpuPinning();
            Set<Integer> requestedCpus = CpuPinningHelper.getAllPinnedPCpus(cpuPinning);
            for (Integer cpuId : requestedCpus) {
                VdsCpuUnit vdsCpuUnit = getCpu(cpuTopology, cpuId);
                if (vdsCpuUnit == null) {
                    // Taking offline CPU, should filter out on CpuPinningPolicyUnit.
                    return new ArrayList<>();
                }
                vdsCpuUnit.pinVm(vm.getId(), vm.getCpuPinningPolicy());
                cpusToBeAllocated.add(vdsCpuUnit);
            }
            return cpusToBeAllocated;
        }

        List<VdsCpuUnit> cpusToBeAllocated = allocateDedicatedCpus(cpuTopology, vm);
        return cpusToBeAllocated.size() == vm.getNumOfCpus() ? cpusToBeAllocated : null;
    }

    private List<VdsCpuUnit> allocateDedicatedCpus(List<VdsCpuUnit> cpuTopology, VM vm) {
        // We can assume that a valid pinning exists here (because the host was filtered beforehand).
        List<VdsCpuUnit> cpusToBeAllocated = new ArrayList<>();
        int socketsLeft = vm.getNumOfSockets();
        int onlineSockets = getOnlineSockets(cpuTopology).size();
        while (onlineSockets > 0 && socketsLeft > 0) {
            List<VdsCpuUnit> cpusInChosenSocket = getMaxFreedSocket(cpuTopology);
            // coreCount is based on the VM topology
            int coreCount = 0;
            for (int core : getOnlineCores(cpusInChosenSocket)) {
                List<VdsCpuUnit> freeCpusInCore = getFreeCpusInCore(cpusInChosenSocket, core);
                int coreThreads = freeCpusInCore.size();
                while (coreThreads >= vm.getThreadsPerCpu() && cpusToBeAllocated.size() < vm.getNumOfCpus()) {
                    for (int thread = 0; thread < vm.getThreadsPerCpu() && cpusToBeAllocated.size() < vm.getNumOfCpus(); thread++) {
                        VdsCpuUnit cpuUnit = freeCpusInCore.remove(0);
                        cpuUnit.pinVm(vm.getId(), vm.getCpuPinningPolicy());
                        cpusToBeAllocated.add(cpuUnit);
                    }
                    coreCount++;
                    coreThreads -= vm.getThreadsPerCpu();
                }
            }
            socketsLeft -= coreCount / vm.getCpuPerSocket();

            onlineSockets--;
        }
        if (socketsLeft > 0) {
            // We didn't manage to allocate the required sockets. We should never get here.
            return Collections.emptyList();
        }
        return cpusToBeAllocated;
    }

    public int countTakenCores(VDS host) {
        List<VdsCpuUnit> cpuTopology = resourceManager.getVdsManager(host.getId()).getCpuTopology();
        if (cpuTopology.isEmpty()) {
            return 0;
        }
        int numOfTakenCores = 0;
        for (int socket : getOnlineSockets(cpuTopology)) {
            for (int core : getOnlineCores(cpuTopology, socket)) {
                if (getNonDedicatedCpusInCore(cpuTopology, socket, core).isEmpty()) {
                    numOfTakenCores++;
                }
            }
        }
        return numOfTakenCores;
    }

    private List<VdsCpuUnit> getCoresInSocket(List<VdsCpuUnit> cpuTopology, int socketId) {
        return cpuTopology.stream().filter(vdsCpuUnit -> vdsCpuUnit.getSocket() == socketId).collect(Collectors.toList());
    }

    private List<VdsCpuUnit> getCpusInCore(List<VdsCpuUnit> cpuTopology, int coreId) {
        return cpuTopology.stream().filter(vdsCpuUnit -> vdsCpuUnit.getCore() == coreId).collect(Collectors.toList());
    }

    private List<VdsCpuUnit> getFreeCpusInSocket(List<VdsCpuUnit> cpuTopology, int socketId) {
        return getCoresInSocket(cpuTopology, socketId).stream().filter(cpu -> !cpu.isPinned()).collect(Collectors.toList());
    }

    private List<VdsCpuUnit> getFreeCpusInCore(List<VdsCpuUnit> cpuTopology, int socketId, int coreId) {
        return getCpusInCore(getCoresInSocket(cpuTopology, socketId), coreId).stream().filter(cpu -> !cpu.isPinned()).collect(Collectors.toList());
    }

    private List<VdsCpuUnit> getFreeCpusInCore(List<VdsCpuUnit> cpuTopology, int coreId) {
        return getCpusInCore(cpuTopology, coreId).stream().filter(cpu -> !cpu.isPinned()).collect(Collectors.toList());
    }

    private VdsCpuUnit getCpu(List<VdsCpuUnit> cpuTopology, int cpuId) {
        return cpuTopology.stream().filter(vdsCpuUnit -> vdsCpuUnit.getCpu() == cpuId).findFirst().orElse(null);
    }

    private List<VdsCpuUnit> getNonDedicatedCpusInCore(List<VdsCpuUnit> cpuTopology, int socketId, int coreId) {
        return getCpusInCore(getCoresInSocket(cpuTopology, socketId), coreId).stream().filter(cpu -> !cpu.isDedicated()).collect(Collectors.toList());
    }

    public int getDedicatedCount(Guid vdsId) {
        return (int) resourceManager.getVdsManager(vdsId).getCpuTopology().stream()
                .filter(VdsCpuUnit::isDedicated).count();
    }

    private List<Integer> getOnlineSockets(List<VdsCpuUnit> cpuTopology) {
        return cpuTopology.stream().map(VdsCpuUnit::getSocket).distinct().collect(Collectors.toList());
    }

    private List<Integer> getOnlineCores(List<VdsCpuUnit> cpuTopology, int socket) {
        return getCoresInSocket(cpuTopology, socket).stream().map(VdsCpuUnit::getCore).distinct().collect(Collectors.toList());
    }

    private List<Integer> getOnlineCores(List<VdsCpuUnit> cpuTopology) {
        return cpuTopology.stream().map(VdsCpuUnit::getCore).distinct().collect(Collectors.toList());
    }

    private List<VdsCpuUnit> getMaxFreedSocket(List<VdsCpuUnit> cpuTopology) {
        List<VdsCpuUnit> chosenSocket = Collections.emptyList();
        List<VdsCpuUnit> temp;
        for (int socket : getOnlineSockets(cpuTopology)) {
            temp = getFreeCpusInSocket(cpuTopology, socket);
            if (temp.size() > chosenSocket.size()) {
                chosenSocket = temp;
            }
        }
        return chosenSocket;
    }

    private int getAvailableCores(List<VdsCpuUnit> cpuTopology, int socket, int vThreads) {
        int count = 0;
        for (int core : getOnlineCores(cpuTopology, socket)) {
            List<VdsCpuUnit> freeCpusInCore = getFreeCpusInCore(cpuTopology, socket, core);
            count += freeCpusInCore.size() / vThreads;
        }
        return count;
    }
}
