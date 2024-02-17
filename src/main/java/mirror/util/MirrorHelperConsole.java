package mirror.util;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.kafka.common.utils.Exit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class MirrorHelperConsole {
    private static final Logger log = LoggerFactory.getLogger(MirrorHelperConsole.class);
    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("connect-mirror-maker");
        parser.description("MirrorMaker Helper");
        parser.addArgument("config").type(Arguments.fileType().verifyCanRead())
                .metavar("mm2.properties").required(true)
                .help("MM2 configuration file.");
        parser.addArgument("--clusters").nargs("+").metavar("CLUSTER").required(false)
                .help("Target cluster to use for this node.");
        Namespace ns;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            Exit.exit(-1);
            return;
        }
        File configFile = ns.get("config");
        List<String> clusters = ns.getList("clusters");
        log.info("Mirror Helper initializing ...");

        MirrorHelperService helperService = new MirrorHelperService(configFile.getPath());
        helperService.setSourceToLatestOffset();
    }
}
