package org.ovirt.engine.core.vdsbroker.storage;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.ovirt.engine.core.common.businessentities.StorageServerConnections;
import org.ovirt.engine.core.common.businessentities.storage.StorageServerConnectionExtension;
import org.ovirt.engine.core.common.utils.Pair;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dao.StorageServerConnectionExtensionDao;

@RunWith(MockitoJUnitRunner.class)
public class StorageConnectionHelperTest {

    @Mock
    private StorageServerConnectionExtensionDao connExtDaoMock;

    private StorageConnectionHelper helper;

    @Before
    public void setup() {
        helper = spy(StorageConnectionHelper.getInstance());
        doReturn(connExtDaoMock).when(helper).getConnectionExtensionDao();
    }

    @Test
    public void testCredentialsWithNoConnectionExtension() {
        StorageServerConnections conn = createConnectionWithCredentials("target1");

        Pair<String, String> credentials = helper.getStorageConnectionCredentialsForhost(Guid.newGuid(), conn);
        assertCredentials(credentials, conn.getUserName(), conn.getPassword());
    }

    @Test
    public void testCredentialsWithConnectionExtension() {
        StorageServerConnections conn = createConnectionWithCredentials("target1");
        StorageServerConnectionExtension connExt = createConnectionExtension(Guid.newGuid());
        when(connExtDaoMock.getByHostIdAndTarget(connExt.getHostId(), connExt.getIqn())).thenReturn(connExt);

        Pair<String, String> credentials = helper.getStorageConnectionCredentialsForhost(connExt.getHostId(), conn);
        assertCredentials(credentials, connExt.getUserName(), connExt.getPassword());
    }

    @Test
    public void testCredentialsWithConnectionExtensionSameHostDifferentTarget() {
        StorageServerConnections conn = createConnectionWithCredentials("target2");
        StorageServerConnectionExtension connExt = createConnectionExtension(Guid.newGuid());
        when(connExtDaoMock.getByHostIdAndTarget(connExt.getHostId(), connExt.getIqn())).thenReturn(connExt);

        Pair<String, String> credentials = helper.getStorageConnectionCredentialsForhost(connExt.getHostId(), conn);
        assertCredentials(credentials, conn.getUserName(), conn.getPassword());
    }

    @Test
    public void testCredentialsWithConnectionExtensionDifferentHostSameTarget() {
        StorageServerConnections conn = createConnectionWithCredentials("target1");
        StorageServerConnectionExtension connExt = createConnectionExtension(Guid.newGuid());
        when(connExtDaoMock.getByHostIdAndTarget(Guid.newGuid(), connExt.getIqn())).thenReturn(connExt);

        Pair<String, String> credentials = helper.getStorageConnectionCredentialsForhost(connExt.getHostId(), conn);
        assertCredentials(credentials, conn.getUserName(), conn.getPassword());
    }

    private static StorageServerConnections createConnectionWithCredentials(String target) {
        StorageServerConnections conn = new StorageServerConnections();
        conn.setIqn(target);
        conn.setUserName("userConn");
        conn.setPassword("pwdConn");
        return conn;
    }

    private static StorageServerConnectionExtension createConnectionExtension(Guid hostId) {
        StorageServerConnectionExtension connExt = new StorageServerConnectionExtension();
        connExt.setHostId(hostId);
        connExt.setIqn("target1");
        connExt.setUserName("userConnExt");
        connExt.setPassword("pwdConnExt");
        return connExt;
    }

    private static void assertCredentials(Pair<String, String> credentials, String userName, String password) {
        assertEquals(userName, credentials.getFirst());
        assertEquals(password, credentials.getSecond());
    }
}
