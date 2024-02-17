package mirror.util;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;
import org.apache.kafka.connect.mirror.MirrorMakerConfig;
import org.apache.kafka.connect.mirror.MirrorSourceConnector;
import org.apache.kafka.connect.mirror.SourceAndTarget;
import org.apache.kafka.connect.runtime.distributed.DistributedConfig;
import org.apache.kafka.connect.runtime.isolation.Plugins;
import org.apache.kafka.connect.storage.Converter;
import org.apache.kafka.connect.storage.KafkaOffsetBackingStore;
import org.apache.kafka.connect.storage.OffsetStorageWriter;
import org.apache.kafka.connect.util.SharedTopicAdmin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class OffsetWriter {
    private static final Logger log = LoggerFactory.getLogger(OffsetWriter.class);
    private final String sourceClusterAlias;
    private final OffsetStorageWriter offsetStorageWriter;

    public OffsetWriter(MirrorMakerConfig mirrorMakerConfig) {
        if(mirrorMakerConfig.clusterPairs().isEmpty())
            throw new IllegalArgumentException("No source->target replication flows.");

        SourceAndTarget sourceAndTarget = mirrorMakerConfig.clusterPairs().get(0);
        sourceClusterAlias = sourceAndTarget.source();

        Plugins plugins = new Plugins(new HashMap<>());
        Map<String, String> internalConverterConfig = Collections.singletonMap(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, "false");
        Converter internalKeyConverter = plugins.newInternalConverter(true, JsonConverter.class.getName(), internalConverterConfig);
        Converter internalValueConverter = plugins.newInternalConverter(false, JsonConverter.class.getName(), internalConverterConfig);

        DistributedConfig distributedConfig = new DistributedConfig(mirrorMakerConfig.workerConfig(sourceAndTarget));
        Map<String, Object> adminProps = new HashMap<>(distributedConfig.originals());
        SharedTopicAdmin sharedAdmin = new SharedTopicAdmin(adminProps);
        KafkaOffsetBackingStore offsetBackingStore = new KafkaOffsetBackingStore(sharedAdmin);
        offsetBackingStore.configure(distributedConfig);
        offsetBackingStore.start();
        offsetStorageWriter = new OffsetStorageWriter(offsetBackingStore, MirrorSourceConnector.class.getSimpleName(),
                internalKeyConverter, internalValueConverter);
    }

    void setOffsets(List<Offset> offsets) {
        offsets.forEach(o -> {
            Map<String, Object> wrappedPartition = MirrorUtils.wrapPartition(new TopicPartition(o.getTopic(), o.getPartition()), sourceClusterAlias);
            Map<String, Object> wrappedOffset = MirrorUtils.wrapOffset(o.getOffset());
            offsetStorageWriter.offset(wrappedPartition, wrappedOffset);
        });
        offsetStorageWriter.beginFlush();
        Future<Void> flushFuture = offsetStorageWriter.doFlush((error, result) -> {
            if (error != null) {
                log.error("{} Failed to flush offsets to storage: ", OffsetWriter.this, error);
            } else {
                log.trace("{} Finished flushing offsets to storage", OffsetWriter.this);
            }
        });
        if (flushFuture == null) {
            offsetStorageWriter.cancelFlush();
            return;
        }

        try {
            flushFuture.get(Math.max(100000, 0), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.warn("{} Flush of offsets interrupted, cancelling", this);
            return;
        } catch (ExecutionException e) {
            log.error("{} Flush of offsets threw an unexpected exception: ", this, e);
            offsetStorageWriter.cancelFlush();
             return;
        } catch (TimeoutException e) {
            log.error("{} Timed out waiting to flush offsets to storage; will try again on next flush interval with latest offsets", this);
            offsetStorageWriter.cancelFlush();
            return;
        }


    }
}
