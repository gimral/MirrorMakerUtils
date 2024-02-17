package mirror.util;

import org.junit.Test;

public class MirrorHelperServiceIT {
    @Test
    public void setOffsetsToLatest(){

        String configPath = MirrorHelperServiceIT.class.getResource("/mm2.properties").getPath();
        MirrorHelperService mirrorHelperService = new MirrorHelperService(configPath);
        mirrorHelperService.setSourceToLatestOffset();
    }
}
