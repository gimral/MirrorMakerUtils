package mirror.util;

import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.connect.mirror.MirrorMakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

public class MirrorHelperService {
    private static final Logger log = LoggerFactory.getLogger(MirrorHelperService.class);
    private MirrorMakerConfig config;
    public MirrorHelperService(String configFilePath){
        loadConfigFile(configFilePath);
    }
    private void loadConfigFile(String configFilePath){
        try {
            Properties props = Utils.loadProps(configFilePath);
            config = new MirrorMakerConfig(Utils.propsToStringMap(props));
        }
        catch (Throwable t) {
            log.error("Stopping due to error", t);
        }
    }

    public void setSourceToLatestOffset() {
        try {
            log.info("Fetching Latest Offsets ...");
            OffsetFetcher offsetFetcher = new OffsetFetcher(config);
            List<Offset> offsets = offsetFetcher.fetchOffsets();

            log.info("Writing Latest Offsets ...");
            OffsetWriter offsetWriter = new OffsetWriter(config);
            offsetWriter.setOffsets(offsets);
        } catch (Throwable t) {
            log.error("Stopping due to error", t);
        }
    }
}
