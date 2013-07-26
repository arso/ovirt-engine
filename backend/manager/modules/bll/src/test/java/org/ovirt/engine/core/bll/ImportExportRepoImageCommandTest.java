package org.ovirt.engine.core.bll;

import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.ovirt.engine.core.bll.lock.InMemoryLockManager;
import org.ovirt.engine.core.bll.provider.OpenStackImageProviderProxy;
import org.ovirt.engine.core.common.businessentities.DiskImage;
import org.ovirt.engine.core.common.businessentities.Provider;
import org.ovirt.engine.core.common.businessentities.ProviderType;
import org.ovirt.engine.core.common.businessentities.StorageDomain;
import org.ovirt.engine.core.common.businessentities.StoragePool;
import org.ovirt.engine.core.common.businessentities.StoragePoolStatus;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dao.DiskImageDAO;
import org.ovirt.engine.core.dao.StorageDomainDAO;
import org.ovirt.engine.core.dao.StoragePoolDAO;
import org.ovirt.engine.core.utils.MockEJBStrategyRule;
import org.ovirt.engine.core.utils.ejb.BeanType;
import org.ovirt.engine.core.utils.lock.LockManager;

import java.util.Arrays;

import static org.mockito.Mockito.when;


public class ImportExportRepoImageCommandTest {

    private LockManager lockManager = new InMemoryLockManager();

    @Rule
    public MockEJBStrategyRule ejbRule = new MockEJBStrategyRule(BeanType.LOCK_MANAGER, lockManager);

    @Mock
    private StorageDomainDAO storageDomainDao;

    @Mock
    private StoragePoolDAO storagePoolDao;

    @Mock
    private DiskImageDAO diskImageDao;

    @Mock
    private OpenStackImageProviderProxy providerProxy;

    private Guid providerId;

    private Guid repoStorageDomainId;

    private Guid storagePoolId;

    private Guid storageDomainId;

    private StoragePool storagePool;

    private String repoImageId;

    private Guid diskImageId;

    private Guid diskImageGroupId;

    private DiskImage diskImage;

    public Guid getRepoStorageDomainId() {
        return repoStorageDomainId;
    }

    public Guid getStoragePoolId() {
        return storagePoolId;
    }

    public Guid getStorageDomainId() {
        return storageDomainId;
    }

    public String getRepoImageId() {
        return repoImageId;
    }

    public StorageDomainDAO getStorageDomainDao() {
        return storageDomainDao;
    }

    public StoragePoolDAO getStoragePoolDao() {
        return storagePoolDao;
    }

    public OpenStackImageProviderProxy getProviderProxy() {
        return providerProxy;
    }

    public StoragePool getStoragePool() {
        return storagePool;
    }

    public Guid getDiskImageId() {
        return diskImageId;
    }

    public Guid getDiskImageGroupId() {
        return diskImageGroupId;
    }

    public DiskImageDAO getDiskImageDao() {
        return diskImageDao;
    }

    public DiskImage getDiskImage() {
        return diskImage;
    }

    @Before
    public void setUp() {
        providerId = Guid.newGuid();
        repoStorageDomainId = Guid.newGuid();

        repoImageId = Guid.newGuid().toString();

        diskImageId = Guid.newGuid();
        diskImageGroupId = Guid.newGuid();

        storagePoolId = Guid.newGuid();
        storageDomainId = Guid.newGuid();

        StorageDomain sourceStorageDomain = new StorageDomain();
        sourceStorageDomain.setStorage(providerId.toString());

        StorageDomain destinationStorageDomain = new StorageDomain();
        destinationStorageDomain.setId(storageDomainId);
        destinationStorageDomain.setStoragePoolId(storagePoolId);

        storagePool  = new StoragePool();
        storagePool.setId(storagePoolId);
        storagePool.setstatus(StoragePoolStatus.Up);

        Provider provider = new Provider();
        provider.setType(ProviderType.OPENSTACK_IMAGE);

        diskImage = new DiskImage();
        diskImage.setId(diskImageId);

        when(storageDomainDao.get(repoStorageDomainId)).thenReturn(sourceStorageDomain);
        when(storageDomainDao.getAllForStorageDomain(storageDomainId))
                .thenReturn(Arrays.asList(destinationStorageDomain));
        when(storagePoolDao.get(storagePoolId)).thenReturn(storagePool);
        when(diskImageDao.get(diskImageId)).thenReturn(diskImage);
        when(providerProxy.getImageAsDiskImage(repoImageId)).thenReturn(diskImage);
    }

}
