package com.elastisys.scale.cloudadapters.openstack.scalinggroup;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.Server.Status;
import org.jclouds.openstack.v2_0.domain.Resource;

import com.elastisys.scale.cloudadapters.commons.adapter.BaseCloudAdapterConfig;
import com.elastisys.scale.cloudadapters.commons.adapter.BaseCloudAdapterConfig.AlertSettings;
import com.elastisys.scale.cloudadapters.commons.adapter.BaseCloudAdapterConfig.MailServerSettings;
import com.elastisys.scale.cloudadapters.commons.adapter.BaseCloudAdapterConfig.ScaleDownConfig;
import com.elastisys.scale.cloudadapters.commons.adapter.BaseCloudAdapterConfig.ScaleUpConfig;
import com.elastisys.scale.cloudadapters.commons.adapter.BaseCloudAdapterConfig.ScalingGroupConfig;
import com.elastisys.scale.cloudadapters.commons.scaledown.VictimSelectionPolicy;
import com.elastisys.scale.commons.json.JsonUtils;
import com.elastisys.scale.commons.util.time.UtcTime;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;

public class TestUtils {

	private static final String USER = "user";
	private static final String TENANT = "Tenant";
	private static final String REGION = "RegionOne";
	private static final String KEYSTONE_ENDPOINT = "http://keystone.host:5000/v2.0/";

	public static BaseCloudAdapterConfig config(String scalingGroupName,
			boolean assignFloatingIp) {
		OpenStackScalingGroupConfig openstackConfig = new OpenStackScalingGroupConfig(
				KEYSTONE_ENDPOINT, REGION, TENANT, USER, "secret",
				assignFloatingIp);
		ScalingGroupConfig scalingGroupConfig = new ScalingGroupConfig(
				scalingGroupName, JsonUtils.toJson(openstackConfig)
						.getAsJsonObject());
		ScaleUpConfig scaleUpConfig = new ScaleUpConfig("m1.small", "",
				"instancekey", Arrays.asList("webserver"), Arrays.asList(
						"#!/bin/bash", "sudo apt-get update -qy",
						"sudo apt-get install apache2 -qy"));
		ScaleDownConfig scaleDownConfig = new ScaleDownConfig(
				VictimSelectionPolicy.CLOSEST_TO_INSTANCE_HOUR, 300);
		AlertSettings alertSettings = new AlertSettings(
				"AwsAsScalingGroup alert",
				Arrays.asList("receiver@destination.com"),
				"noreply@elastisys.com", "ERROR|FATAL", new MailServerSettings(
						"smtp.host.com", 25, null, false));
		Integer poolUpdatePeriod = 60;
		return new BaseCloudAdapterConfig(scalingGroupConfig, scaleUpConfig,
				scaleDownConfig, alertSettings, poolUpdatePeriod);
	}

	/**
	 * Creates a list of servers.
	 *
	 * @param servers
	 * @return
	 */
	public static List<Server> servers(Server... servers) {
		return Lists.newArrayList(servers);
	}

	/**
	 * Creates a {@link Server} with given id and status and with no metadata
	 * tags set.
	 *
	 * @param id
	 *            The id to assign to the {@link Server}.
	 * @param status
	 *            The {@link Status} to set for the {@link Server}.
	 * @return
	 */
	public static Server server(String id, Status status) {
		ListMultimap<String, Address> ipAddresses = ArrayListMultimap.create();
		return Server.builder().id(id).status(status).tenantId(TENANT)
				.userId(USER).created(UtcTime.now().toDate())
				.image(Resource.builder().id("imageId").build())
				.flavor(Resource.builder().id("flavorId").build())
				.addresses(ipAddresses).build();
	}

	/**
	 * Creates a {@link Server} with given id and status.
	 *
	 * @param id
	 *            The id to assign to the {@link Server}.
	 * @param status
	 *            The {@link Status} to set for the {@link Server}.
	 * @param metadataTags
	 *            Any metadata tags to set on the {@link Server}.
	 * @return
	 */
	public static Server server(String id, Status status,
			Map<String, String> metadataTags) {
		ListMultimap<String, Address> ipAddresses = ArrayListMultimap.create();
		return Server.builder().id(id).status(status).tenantId(TENANT)
				.userId(USER).created(UtcTime.now().toDate())
				.metadata(metadataTags)
				.image(Resource.builder().id("imageId").build())
				.flavor(Resource.builder().id("flavorId").build())
				.addresses(ipAddresses).build();
	}
}
