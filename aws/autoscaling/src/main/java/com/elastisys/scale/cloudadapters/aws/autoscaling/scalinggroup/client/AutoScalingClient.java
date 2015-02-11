package com.elastisys.scale.cloudadapters.aws.autoscaling.scalinggroup.client;

import java.util.List;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;
import com.elastisys.scale.cloudadapers.api.NotFoundException;
import com.elastisys.scale.cloudadapters.aws.autoscaling.scalinggroup.AwsAsScalingGroup;
import com.elastisys.scale.cloudadapters.aws.autoscaling.scalinggroup.AwsAsScalingGroupConfig;

/**
 * A simplified client interface towards the AWS Auto Scaling API that only
 * provides the functionality needed by the {@link AwsAsScalingGroup}.
 * <p/>
 * The {@link #configure} method must be called before calling any other
 * methods.
 *
 *
 *
 */
public interface AutoScalingClient {

	/**
	 * Configures this {@link AutoScalingClient} with credentials to allow it to
	 * access the AWS Auto Scaling API.
	 *
	 * @param configuration
	 *            A client configuration.
	 */
	void configure(AwsAsScalingGroupConfig configuration);

	/**
	 * Retrieves a particular {@link AutoScalingGroup}.
	 *
	 * @param autoScalingGroupName
	 *            The name of the Auto Scaling Group.
	 * @return
	 */
	AutoScalingGroup getAutoScalingGroup(String autoScalingGroupName);

	/**
	 * Retrieves all members of a particular {@link AutoScalingGroup}.
	 *
	 * @param autoScalingGroupName
	 *            The name of the Auto Scaling Group.
	 * @return
	 */
	List<Instance> getAutoScalingGroupMembers(String autoScalingGroupName);

	/**
	 * Sets the desired capacity of a particular Auto Scaling Group.
	 * <p/>
	 * Note that this method will not block until the group has reached the
	 * desired size, which may be a time-consuming process.
	 *
	 * @param autoScalingGroupName
	 *            The name of the Auto Scaling Group.
	 * @param desiredSize
	 *            The desired capacity of the group to set.
	 */
	void setDesiredSize(String autoScalingGroupName, int desiredSize);

	/**
	 * Terminates a particular Auto Scaling Group member instance. As a
	 * side-effect, the desired capacity of the Auto Scaling Group is
	 * decremented.
	 *
	 * @param autoScalingGroupName
	 *            The name of the Auto Scaling Group.
	 * @param instanceId
	 *            An instance identifier of a member in the Auto Scaling Group.
	 * @throws NotFoundException
	 *             if the instance does not exist
	 */
	void terminateInstance(String autoScalingGroupName, String instanceId)
			throws NotFoundException;

	/**
	 * Adds a machine instance to the scaling group. As a side-effect, the
	 * desired capacity of the Auto Scaling Group is incremented.
	 *
	 * @param machineId
	 *            The identifier of the instance to attach to the pool.
	 * @throws NotFoundException
	 *             if the instance does not exist
	 */
	void attachInstance(String autoScalingGroupName, String instanceId)
			throws NotFoundException;

	/**
	 * Removes a member from the scaling group without terminating it. As a
	 * side-effect, the desired capacity of the Auto Scaling Group is
	 * decremented.
	 *
	 * @param machineId
	 *            The identifier of the instance to detach from the pool.
	 * @throws NotFoundException
	 *             if the instance does not exist
	 */
	void detachInstance(String autoScalingGroupName, String instanceId)
			throws NotFoundException;

	/**
	 * Sets a collection of tags on an EC2 instance.
	 *
	 * @param instanceId
	 *            An instance identifier.
	 * @param tags
	 *            The {@link Tag}s to set on the {@link Instance}.
	 * @throws NotFoundException
	 *             if the instance does not exist
	 */
	void tagInstance(String instanceId, List<Tag> tags)
			throws NotFoundException;

}
