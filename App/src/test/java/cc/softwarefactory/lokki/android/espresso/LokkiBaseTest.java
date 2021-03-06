package cc.softwarefactory.lokki.android.espresso;

import android.content.res.Resources;
import android.test.ActivityInstrumentationTestCase2;

import com.squareup.okhttp.mockwebserver.MockWebServer;

import cc.softwarefactory.lokki.android.MainActivity;
import cc.softwarefactory.lokki.android.ServerAPI;
import cc.softwarefactory.lokki.android.espresso.utilities.MockDispatcher;
import cc.softwarefactory.lokki.android.espresso.utilities.TestUtils;

/**
 * Abstract base class for tests that want to have a mock HTTP server running on each test. Also has
 * some convenient helpers.
 */
public abstract class LokkiBaseTest extends ActivityInstrumentationTestCase2<MainActivity>  {

    private MockWebServer mockWebServer;
    private MockDispatcher mockDispatcher;

    public LokkiBaseTest() {
        super(MainActivity.class);
    }

    protected Resources getResources() {
        return getInstrumentation().getTargetContext().getResources();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestUtils.clearAppData(getInstrumentation().getTargetContext());

        mockWebServer = new MockWebServer();
        mockDispatcher = new MockDispatcher();
        mockWebServer.setDispatcher(mockDispatcher);
        mockWebServer.play();

        String mockUrl = mockWebServer.getUrl("/").toString();
        ServerAPI.setApiUrl(mockUrl);
    }

    @Override
    protected void tearDown() throws Exception {
        mockWebServer.shutdown();

        // clearing up app data on tearDown too, so there won't be any leftover app data from tests
        // if user is running application normally after running tests
        TestUtils.clearAppData(getInstrumentation().getTargetContext());

        super.tearDown();
    }

    public MockDispatcher getMockDispatcher() {
        return mockDispatcher;
    }
}
