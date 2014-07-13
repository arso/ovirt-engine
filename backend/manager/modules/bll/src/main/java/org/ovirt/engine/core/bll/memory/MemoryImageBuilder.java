package org.ovirt.engine.core.bll.memory;

import java.util.List;

import org.ovirt.engine.core.common.businessentities.DiskImage;

public interface MemoryImageBuilder {
    /**
     * Create the images
     */
    void build();

    /**
     * Return the string representation of the memory volumes
     * @return string representation of the memory volumes
     */
    String getVolumeStringRepresentation();

    /**
     * Return whether this builder creates tasks in {@link #build()}
     * @return true if tasks are created in {@link #build()}, false otherwise
     */
    boolean isCreateTasks();

    List<DiskImage> getDisksToBeCreated();
}
