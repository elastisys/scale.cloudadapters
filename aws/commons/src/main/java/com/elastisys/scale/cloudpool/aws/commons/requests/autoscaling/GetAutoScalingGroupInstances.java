package com.elastisys.scale.cloudpool.aws.commons.requests.autoscaling;

import static com.elastisys.scale.cloudpool.aws.commons.predicates.InstancePredicates.instancesPresent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.elastisys.scale.cloudpool.aws.commons.requests.ec2.GetInstances;
import com.elastisys.scale.commons.net.retryable.Retryable;
import com.elastisys.scale.commons.net.retryable.Retryers;

/**
 * A {@link Callable} task that, when executed, requests details about all
 * instances belonging to a particular AWS Auto Scaling Group in a region.
 *
 * @see AutoScalingInstance
 */
public class GetAutoScalingGroupInstances extends AmazonAutoScalingRequest<List<Instance>> {

    /** The name of the {@link AutoScalingGroup} of interest. */
    private final String groupName;

    /**
     * Constructs a new {@link GetAutoScalingGroupInstances} task.
     *
     * @param awsCredentials
     * @param region
     * @param clientConfig
     *            Client configuration options such as connection timeout, etc.
     * @param groupName
     */
    public GetAutoScalingGroupInstances(AWSCredentials awsCredentials, String region, ClientConfiguration clientConfig,
            String groupName) {
        super(awsCredentials, region, clientConfig);
        this.groupName = groupName;
    }

    @Override
    public List<Instance> call() {
        AutoScalingGroup autoScalingGroup = new GetAutoScalingGroup(getAwsCredentials(), getRegion(), getClientConfig(),
                this.groupName).call();

        try {
            return listGroupInstances(autoScalingGroup);
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed waiting for auto scaling group members: %s", e.getMessage()), e);
        }
    }

    private List<Instance> listGroupInstances(AutoScalingGroup autoScalingGroup) throws Exception {
        List<String> instanceIds = autoScalingGroup.getInstances().stream()
                .map(com.amazonaws.services.autoscaling.model.Instance::getInstanceId).collect(Collectors.toList());
        if (instanceIds.isEmpty()) {
            // note: we don't want to call get instances with an emtpy list
            // since this causes DescribeInstances to get *all* instances in the
            // region (not just the ones in our Auto Scaling Group, which is
            // what we want)
            return new ArrayList<>();
        }

        List<Filter> filters = Collections.emptyList();
        Callable<List<Instance>> requester = new GetInstances(getAwsCredentials(), getRegion(), getClientConfig(),
                instanceIds, filters);

        int initialDelay = 1;
        int maxAttempts = 10; // max 2 ^ 9 - 1 seconds = 511 seconds
        String name = String.format("await-describe-instances");
        Retryable<List<Instance>> retryer = Retryers.exponentialBackoffRetryer(name, requester, initialDelay,
                TimeUnit.SECONDS, maxAttempts, instancesPresent(instanceIds));

        return retryer.call();
    }
}
