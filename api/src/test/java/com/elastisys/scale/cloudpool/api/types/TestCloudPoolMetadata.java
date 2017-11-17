package com.elastisys.scale.cloudpool.api.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import com.elastisys.scale.commons.json.JsonUtils;

/**
 * Exercises the {@link CloudPoolMetadata} class.
 */
public class TestCloudPoolMetadata {

    @Test
    public void equalsAndHashcode() {
        List<String> supportedApiVersions = Arrays.asList("1.0", "2.0", "3.0");

        CloudPoolMetadata cpm1 = new CloudPoolMetadata(CloudProviders.AWS_AUTO_SCALING_GROUP, supportedApiVersions);
        CloudPoolMetadata cpm2 = new CloudPoolMetadata(CloudProviders.AWS_AUTO_SCALING_GROUP, supportedApiVersions);
        CloudPoolMetadata cpm3 = new CloudPoolMetadata(CloudProviders.OPENSTACK, supportedApiVersions);

        assertEquals(cpm1, cpm2);
        assertEquals(cpm1.hashCode(), cpm2.hashCode());
        assertNotEquals(cpm1, cpm3);
        assertNotEquals(cpm1.hashCode(), cpm3.hashCode());

        assertEquals(cpm2, cpm1);
        assertEquals(cpm2.hashCode(), cpm1.hashCode());
        assertNotEquals(cpm2, cpm3);
        assertNotEquals(cpm2.hashCode(), cpm3.hashCode());

        assertNotEquals(cpm3, cpm1);
        assertNotEquals(cpm3.hashCode(), cpm1.hashCode());
        assertNotEquals(cpm3, cpm2);
        assertNotEquals(cpm3.hashCode(), cpm2.hashCode());
    }

    @Test(expected = IllegalStateException.class)
    public void emptyVersionsList() {
        new CloudPoolMetadata(CloudProviders.AWS_EC2, new LinkedList<String>());
    }

    @Test(expected = NullPointerException.class)
    public void nullVersionsList() {
        new CloudPoolMetadata(CloudProviders.AWS_EC2, null);
    }

    @Test(expected = NullPointerException.class)
    public void nullIdentifier() {
        final List<String> supportedApiVersions = Arrays.asList("1.0", "2.0", "3.14");
        new CloudPoolMetadata((String) null, supportedApiVersions);
    }

    @Test(expected = IllegalStateException.class)
    public void incorrectVersionString() {
        new CloudPoolMetadata(CloudProviders.AWS_EC2, Arrays.asList("1.0a"));
    }

    @Test(expected = IllegalStateException.class)
    public void incorrectVersionString2() {
        new CloudPoolMetadata(CloudProviders.AWS_EC2, Arrays.asList("1."));
    }

    @Test(expected = IllegalStateException.class)
    public void incorrectVersionString3() {
        new CloudPoolMetadata(CloudProviders.AWS_EC2, Arrays.asList(" 1.0"));
    }

    @Test(expected = IllegalStateException.class)
    public void incorrectVersionString4() {
        new CloudPoolMetadata(CloudProviders.AWS_EC2, Arrays.asList(""));
    }

    @Test
    public void correctVersionString() {
        new CloudPoolMetadata(CloudProviders.AWS_EC2, Arrays.asList("1"));
    }

    @Test
    public void trivialJsonParsing() {
        final List<String> supportedApiVersions = Arrays.asList("1.0", "2.0", "3.0");
        final String handMadeJson = generateJson(CloudProviders.AWS_EC2, "true", supportedApiVersions);

        final CloudPoolMetadata expected = new CloudPoolMetadata(CloudProviders.AWS_EC2, supportedApiVersions);

        // hand-made valid JSON
        assertEquals(expected, JsonUtils.toObject(JsonUtils.parseJsonString(handMadeJson), CloudPoolMetadata.class));

        // generated JSON
        assertEquals(expected, JsonUtils.toObject(JsonUtils.toJson(expected), CloudPoolMetadata.class));
    }

    private String generateJson(String poolIdentifier, String cloudSupportsRequesttime,
            List<String> supportedApiVersions) {
        StringBuilder sb = new StringBuilder();

        sb.append("{");

        sb.append("\"poolIdentifier\": \"");
        sb.append(poolIdentifier);
        sb.append("\", ");

        if (supportedApiVersions != null) {
            sb.append("\"supportedApiVersions\": ");
            sb.append(JsonUtils.toString(JsonUtils.toJson(supportedApiVersions)));
        }

        sb.append("}");

        return sb.toString();
    }

}
