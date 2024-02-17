package mirror.util;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.mirror.*;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OffsetFetcher {
    private final AdminClient sourceAdminClient;
    private final TopicFilter topicFilter;
    private final ReplicationPolicy replicationPolicy;
    private final SourceAndTarget sourceAndTarget;

    public OffsetFetcher(MirrorMakerConfig mirrorConfig) {
        sourceAndTarget = mirrorConfig.clusterPairs().get(0);
        Map<String, String> props = mirrorConfig.connectorBaseConfig(sourceAndTarget, MirrorSourceConnector.class);
        MirrorConnectorConfig config = new MirrorConnectorConfig(props);

        sourceAdminClient = AdminClient.create(config.sourceAdminConfig());
        topicFilter = config.topicFilter();
        replicationPolicy = config.replicationPolicy();
    }

    public List<Offset> fetchOffsets() throws ExecutionException, InterruptedException {
        Map<TopicPartition, OffsetSpec> offsetRequest = findSourceTopicPartitions().stream().collect(Collectors.toMap(t -> t, t -> OffsetSpec.latest()));

        // Iterate over topics and get the latest offset for each topic
        return sourceAdminClient.listOffsets(offsetRequest)
                .all().get()
                .entrySet()
                .stream()
                .filter(t -> t.getValue().offset() > 0)
                .map(t -> new Offset(t.getKey().topic(),t.getKey().partition(),t.getValue().offset() - 1 ))
                .collect(Collectors.toList());
    }

    List<TopicPartition> findSourceTopicPartitions()
            throws InterruptedException, ExecutionException {
        Set<String> topics = listTopics(sourceAdminClient).stream()
                .filter(this::shouldReplicateTopic)
                .collect(Collectors.toSet());
        return describeTopics(sourceAdminClient, topics).stream()
                .flatMap(OffsetFetcher::expandTopicDescription)
                .collect(Collectors.toList());
    }

    private Set<String> listTopics(AdminClient adminClient)
            throws InterruptedException, ExecutionException {
        return adminClient.listTopics().names().get();
    }

    private Collection<TopicDescription> describeTopics(AdminClient adminClient, Collection<String> topics)
            throws InterruptedException, ExecutionException {
        return adminClient.describeTopics(topics).allTopicNames().get().values();
    }

    private static Stream<TopicPartition> expandTopicDescription(TopicDescription description) {
        String topic = description.name();
        return description.partitions().stream()
                .map(x -> new TopicPartition(topic, x.partition()));
    }


    boolean shouldReplicateTopic(String topic) {
        return (topicFilter.shouldReplicateTopic(topic) || replicationPolicy.isHeartbeatsTopic(topic))
                && !replicationPolicy.isInternalTopic(topic) && !isCycle(topic);
    }
    boolean isCycle(String topic) {
        String source = replicationPolicy.topicSource(topic);
        if (source == null) {
            return false;
        } else if (source.equals(sourceAndTarget.target())) {
            return true;
        } else {
            String upstreamTopic = replicationPolicy.upstreamTopic(topic);
            if (upstreamTopic.equals(topic)) {
                // Extra check for IdentityReplicationPolicy and similar impls that don't prevent cycles.
                return false;
            }
            return isCycle(upstreamTopic);
        }
    }
}
