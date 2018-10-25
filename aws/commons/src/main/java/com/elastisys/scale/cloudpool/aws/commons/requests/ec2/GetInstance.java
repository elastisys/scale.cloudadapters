package com.elastisys.scale.cloudpool.aws.commons.requests.ec2;

import static java.lang.String.format;

import java.util.concurrent.Callable;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.elastisys.scale.cloudpool.api.NotFoundException;
import com.elastisys.scale.commons.net.retryable.Retryable;

/**
 * A {@link Callable} task that, when executed, requests details about a
 * particular AWS EC2 machine {@link Instance} in a region.
 * <p/>
 * Due to the eventual consistency semantics of the Amazon API, a recently
 * launched instance may not show up immediately when calling
 * {@code DescribeInstances}.
 * <p/>
 * To wait for an instance to become available, this could be used with a
 * {@link Retryable}.
 */
public class GetInstance extends AmazonEc2Request<Instance> {

    /** The identifier of {@link Instance} interest. */
    private final String instanceId;

    /**
     * Constructs a new {@link GetInstance} request task.
     *
     * @param awsCredentials
     * @param region
     * @param clientConfig
     *            Client configuration options such as connection timeout, etc.
     * @param instanceId
     */
    public GetInstance(AWSCredentials awsCredentials, String region, ClientConfiguration clientConfig,
            String instanceId) {
        super(awsCredentials, region, clientConfig);
        this.instanceId = instanceId;
    }

    @Override
    public Instance call() throws NotFoundException {
        DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(this.instanceId);
        DescribeInstancesResult result = getClient().getApi().describeInstances(request);
        if (result.getReservations().isEmpty()) {
            throw new NotFoundException(format("DescribeInstances: no such instance exists: '%s'", this.instanceId));
        }
        Reservation reservation = result.getReservations().get(0);
        Instance instance = reservation.getInstances().get(0);
        return instance;
    }

}
