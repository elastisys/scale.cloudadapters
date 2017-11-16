package com.elastisys.scale.cloudpool.aws.spot.driver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestState;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.CancelledSpotInstanceRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.SpotInstanceState;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.elastisys.scale.cloudpool.api.NotFoundException;
import com.elastisys.scale.cloudpool.aws.commons.ScalingFilters;
import com.elastisys.scale.cloudpool.aws.commons.poolclient.Ec2ProvisioningTemplate;
import com.elastisys.scale.cloudpool.aws.commons.poolclient.SpotClient;
import com.google.common.collect.Lists;

/**
 * A fake {@link SpotClient} that can be set up to simulate an EC2 account with
 * a number of machine {@link Instance}s and {@link SpotInstanceRequest}s.
 */
public class FakeSpotClient implements SpotClient {
    private static Logger LOG = LoggerFactory.getLogger(FakeSpotClient.class);

    /**
     * {@link Filter} matchers used when filtering {@link SpotInstanceRequest}s.
     */
    private static final Map<String, SpotRequestFilterMatcher> spotRequestFilterMatchers = new TreeMap<>();

    static {
        spotRequestFilterMatchers.put(ScalingFilters.SPOT_REQUEST_ID_FILTER, new SpotRequestIdFilterMatcher());
        spotRequestFilterMatchers.put(ScalingFilters.SPOT_REQUEST_STATE_FILTER, new SpotRequestStateFilterMatcher());
        spotRequestFilterMatchers.put(ScalingFilters.CLOUD_POOL_TAG_FILTER, new SpotRequestCloudPoolFilterMatcher());

    }

    /** {@link Filter} matchers used when filtering {@link Instance}s. */
    private static final Map<String, InstanceFilterMatcher> instanceFilterMatchers = new TreeMap<>();

    static {
        instanceFilterMatchers.put(ScalingFilters.INSTANCE_STATE_FILTER, new InstanceStateFilterMatcher());
        instanceFilterMatchers.put(ScalingFilters.SPOT_REQUEST_ID_FILTER, new InstanceSpotRequestFilterMatcher());
    }

    private final Map<String, SpotInstanceRequest> spotRequests;
    private final Map<String, Instance> instances;
    private int idSequencer = 0;

    public FakeSpotClient() {
        this.spotRequests = new HashMap<>();
        this.instances = new HashMap<>();
    }

    /**
     * Adds a number of {@link SpotInstanceRequest}s and {@link Instance}s to
     * the faked cloud account.
     *
     * @param spotRequests
     * @param instances
     */
    public void setupFakeAccount(Collection<SpotInstanceRequest> spotRequests, Collection<Instance> instances) {
        for (SpotInstanceRequest request : spotRequests) {
            this.spotRequests.put(request.getSpotInstanceRequestId(), request);
        }
        for (Instance instance : instances) {
            this.instances.put(instance.getInstanceId(), instance);
        }
    }

    @Override
    public void configure(String awsAccessKeyId, String awsSecretAccessKey, String region,
            ClientConfiguration clientConfiguration) {
        LOG.debug(getClass().getSimpleName() + " configured");
    }

    @Override
    public List<Instance> getInstances(List<Filter> filters) throws AmazonClientException {
        List<Instance> instances = Lists.newArrayList(this.instances.values());
        // filter out instances that don't match all filters
        Iterator<Instance> iterator = instances.iterator();
        while (iterator.hasNext()) {
            Instance instance = iterator.next();
            for (Filter filter : filters) {
                if (!matches(filter, instance)) {
                    // filter out
                    iterator.remove();
                    break;
                }
            }
        }
        // return sorted on identifier to ease verifications in tests
        Collections.sort(instances, (o1, o2) -> o1.getInstanceId().compareTo(o2.getInstanceId()));
        return instances;
    }

    @Override
    public Instance getInstanceMetadata(String instanceId) throws NotFoundException, AmazonClientException {
        if (!this.instances.containsKey(instanceId)) {
            throw new AmazonServiceException(String.format(
                    "The instance ID '%s' does not exist (Service: AmazonEC2; " + "Status Code: 400; Error Code: "
                            + "InvalidInstanceID.NotFound; " + "Request ID: ea111d31-62e8-41b7-97b8-95719b0fa055)",
                    instanceId));
        }
        return this.instances.get(instanceId);
    }

    @Override
    public List<Instance> launchInstances(Ec2ProvisioningTemplate template, int count) throws AmazonClientException {

        List<Instance> launchedInstances = Lists.newArrayList();
        for (int i = 0; i < count; i++) {
            int idNum = ++this.idSequencer;
            String id = "i-" + idNum;
            LOG.info("launching instance {} into fake account ...", id);
            Instance newInstance = new Instance().withInstanceId(id).withState(new InstanceState().withName("pending"))
                    .withPublicIpAddress("1.2.3." + idNum).withImageId(template.getAmiId())
                    .withInstanceType(template.getInstanceType());
            if (!template.getTags().isEmpty()) {
                List<Tag> tags = new ArrayList<>();
                for (Entry<String, String> tag : template.getTags().entrySet()) {
                    tags.add(new Tag(tag.getKey(), tag.getValue()));
                }
                newInstance.withTags(tags);
            }
            this.instances.put(newInstance.getInstanceId(), newInstance);
            launchedInstances.add(newInstance);
        }
        return launchedInstances;
    }

    @Override
    public void tagResource(String resourceId, List<Tag> tags) throws AmazonClientException {
        if (!this.instances.containsKey(resourceId) && !this.spotRequests.containsKey(resourceId)) {
            throw new AmazonServiceException(String.format("The ID '%s' is not valid (Service: AmazonEC2; "
                    + "Status Code: 400; Error Code: InvalidID; " + "Request ID: 05ee0a2b-737b-4749-9b83-eb7eec710c68)",
                    resourceId));
        }
        if (this.instances.containsKey(resourceId)) {
            List<Tag> instanceTags = this.instances.get(resourceId).getTags();
            instanceTags.addAll(tags);
        }

        if (this.spotRequests.containsKey(resourceId)) {
            List<Tag> spotTags = this.spotRequests.get(resourceId).getTags();
            spotTags.addAll(tags);
        }
    }

    @Override
    public void untagResource(String resourceId, List<Tag> tags) throws AmazonClientException {
        if (!this.instances.containsKey(resourceId) && !this.spotRequests.containsKey(resourceId)) {
            throw new AmazonServiceException(String.format("The ID '%s' is not valid (Service: AmazonEC2; "
                    + "Status Code: 400; Error Code: InvalidID; " + "Request ID: 05ee0a2b-737b-4749-9b83-eb7eec710c68)",
                    resourceId));
        }
        if (this.instances.containsKey(resourceId)) {
            this.instances.get(resourceId).getTags().removeAll(tags);
        }

        if (this.spotRequests.containsKey(resourceId)) {
            this.spotRequests.get(resourceId).getTags().removeAll(tags);
        }

    }

    @Override
    public TerminateInstancesResult terminateInstances(List<String> instanceIds)
            throws NotFoundException, AmazonClientException {
        TerminateInstancesResult result = new TerminateInstancesResult();
        for (String instanceId : instanceIds) {
            if (!this.instances.containsKey(instanceId)) {
                throw new AmazonServiceException(String.format(
                        "The instance ID '%s' does not exist " + "(Service: AmazonEC2; Status Code: 400; Error Code: "
                                + "InvalidInstanceID.NotFound;" + " Request ID: 12a2ebaf-c480-4998-95fb-6d47b4393e00)",
                        instanceId));
            }
            this.instances.remove(instanceId);

            result.withTerminatingInstances(new InstanceStateChange().withInstanceId(instanceId)
                    .withCurrentState(new InstanceState().withName(InstanceStateName.ShuttingDown)));

        }
        return result;
    }

    @Override
    public SpotInstanceRequest getSpotInstanceRequest(String spotRequestId) throws AmazonClientException {
        if (!this.spotRequests.containsKey(spotRequestId)) {
            throw new AmazonServiceException(
                    String.format("The spot instance request ID '%s' does not exist (Service: AmazonEC2; "
                            + "Status Code: 400; Error Code: " + "InvalidSpotInstanceRequestID.NotFound; "
                            + "Request ID: 5fc1854f-ceb4-4f6a-8178-a9e918371d46)", spotRequestId));
        }
        return this.spotRequests.get(spotRequestId);
    }

    @Override
    public List<SpotInstanceRequest> getSpotInstanceRequests(Collection<Filter> filters) throws AmazonClientException {
        List<SpotInstanceRequest> requests = Lists.newArrayList(this.spotRequests.values());
        // filter out requests that don't match all filters
        Iterator<SpotInstanceRequest> iterator = requests.iterator();
        while (iterator.hasNext()) {
            SpotInstanceRequest request = iterator.next();
            for (Filter filter : filters) {
                if (!matches(filter, request)) {
                    // filter out
                    iterator.remove();
                    break;
                }
            }
        }
        // return sorted on identifier to ease verifications in tests
        Collections.sort(requests, (o1, o2) -> o1.getSpotInstanceRequestId().compareTo(o2.getSpotInstanceRequestId()));
        return requests;
    }

    @Override
    public List<SpotInstanceRequest> placeSpotRequests(double bidPrice, Ec2ProvisioningTemplate template, int count)
            throws AmazonClientException {
        List<SpotInstanceRequest> requests = Lists.newArrayList();
        for (int i = 0; i < count; i++) {
            String id = "sir-" + (System.currentTimeMillis() + count);
            SpotInstanceRequest request = new SpotInstanceRequest().withSpotInstanceRequestId(id)
                    .withLaunchSpecification(new LaunchSpecification().withInstanceType(template.getInstanceType()))
                    .withState(SpotInstanceState.Open).withSpotPrice(String.valueOf(bidPrice));

            if (!template.getTags().isEmpty()) {
                List<Tag> tags = new ArrayList<>();
                for (Entry<String, String> tag : template.getTags().entrySet()) {
                    tags.add(new Tag(tag.getKey(), tag.getValue()));
                }
                request.withTags(tags);
            }
            this.spotRequests.put(id, request);
            requests.add(request);
        }
        return requests;
    }

    @Override
    public CancelSpotInstanceRequestsResult cancelSpotRequests(List<String> spotInstanceRequestIds)
            throws AmazonClientException {
        CancelSpotInstanceRequestsResult result = new CancelSpotInstanceRequestsResult();
        for (String spotInstanceRequestId : spotInstanceRequestIds) {
            getSpotInstanceRequest(spotInstanceRequestId).setState(SpotInstanceState.Cancelled);

            result.withCancelledSpotInstanceRequests(
                    new CancelledSpotInstanceRequest().withSpotInstanceRequestId(spotInstanceRequestId)
                            .withState(CancelSpotInstanceRequestState.Cancelled));
        }
        return result;
    }

    private boolean matches(Filter filter, SpotInstanceRequest spotRequest) {
        String filterName = filter.getName();
        if (!spotRequestFilterMatchers.containsKey(filterName)) {
            throw new IllegalArgumentException("fake SpotClient does not recognize spot filter " + filterName);
        }
        return spotRequestFilterMatchers.get(filterName).matches(spotRequest, filter);
    }

    private boolean matches(Filter filter, Instance instance) {
        String filterName = filter.getName();
        if (!instanceFilterMatchers.containsKey(filterName)) {
            throw new IllegalArgumentException("fake SpotClient does not recognize instance filter " + filterName);
        }
        return instanceFilterMatchers.get(filterName).matches(instance, filter);
    }

    private static interface InstanceFilterMatcher {
        boolean matches(Instance instance, Filter filter);
    }

    private static interface SpotRequestFilterMatcher {
        boolean matches(SpotInstanceRequest spotRequest, Filter filter);
    }

    private static class SpotRequestIdFilterMatcher implements SpotRequestFilterMatcher {
        @Override
        public boolean matches(SpotInstanceRequest spotRequest, Filter filter) {
            return filter.getValues().contains(spotRequest.getSpotInstanceRequestId());
        }
    }

    private static class SpotRequestStateFilterMatcher implements SpotRequestFilterMatcher {
        @Override
        public boolean matches(SpotInstanceRequest spotRequest, Filter filter) {
            return filter.getValues().contains(spotRequest.getState());
        }
    }

    private static class SpotRequestCloudPoolFilterMatcher implements SpotRequestFilterMatcher {
        @Override
        public boolean matches(SpotInstanceRequest spotRequest, Filter filter) {
            // filter name is "tag:<tagname>"
            String expectedTag = filter.getName().replace("tag:", "");
            List<String> acceptableTagValues = filter.getValues();
            for (Tag tag : spotRequest.getTags()) {
                if (tag.getKey().equals(expectedTag)) {
                    return acceptableTagValues.contains(tag.getValue());
                }
            }
            return filter.getValues().contains(spotRequest.getState());
        }
    }

    private static class InstanceStateFilterMatcher implements InstanceFilterMatcher {
        @Override
        public boolean matches(Instance instance, Filter filter) {
            return filter.getValues().contains(instance.getState().getName());
        }
    }

    private static class InstanceSpotRequestFilterMatcher implements InstanceFilterMatcher {
        @Override
        public boolean matches(Instance instance, Filter filter) {
            return filter.getValues().contains(instance.getSpotInstanceRequestId());
        }
    }

}
