package com.elastisys.scale.cloudpool.aws.commons.predicates;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.amazonaws.services.ec2.model.SpotInstanceRequest;

public class TestSpotRequestPredicates {

    /**
     * Test the {@link SpotRequestPredicates#inAnyOfStates(String...)} predicate
     * when there is only a single state to match {@link SpotInstanceRequest}s
     * against.
     */
    @Test
    public void testInAnyOfStatesPredicateWithSingleMatchingState() {
        // make sure all valid states are recognized
        assertTrue(SpotRequestPredicates.inAnyOfStates("active").test(spotRequest("sir-1", "active")));
        assertTrue(SpotRequestPredicates.inAnyOfStates("closed").test(spotRequest("sir-1", "closed")));
        assertTrue(SpotRequestPredicates.inAnyOfStates("cancelled").test(spotRequest("sir-1", "cancelled")));
        assertTrue(SpotRequestPredicates.inAnyOfStates("failed").test(spotRequest("sir-1", "failed")));
        assertTrue(SpotRequestPredicates.inAnyOfStates("open").test(spotRequest("sir-1", "open")));
    }

    /**
     * Test the {@link SpotRequestPredicates#inAnyOfStates(String...)} predicate
     * when there are more than one state to match {@link SpotInstanceRequest}s
     * against.
     */
    @Test
    public void testInAnyOfStatesPredicateWithMultipleMatchingStates() {

        assertTrue(SpotRequestPredicates.inAnyOfStates("closed", "cancelled", "failed")
                .test(spotRequest("sir-1", "closed")));
        assertTrue(SpotRequestPredicates.inAnyOfStates("closed", "cancelled", "failed")
                .test(spotRequest("sir-1", "cancelled")));
        assertTrue(SpotRequestPredicates.inAnyOfStates("closed", "cancelled", "failed")
                .test(spotRequest("sir-1", "failed")));
        assertFalse(SpotRequestPredicates.inAnyOfStates("closed", "cancelled", "failed")
                .test(spotRequest("sir-1", "active")));
        assertFalse(SpotRequestPredicates.inAnyOfStates("closed", "cancelled", "failed")
                .test(spotRequest("sir-1", "open")));

        assertTrue(SpotRequestPredicates.inAnyOfStates("active", "open").test(spotRequest("sir-1", "active")));
        assertTrue(SpotRequestPredicates.inAnyOfStates("active", "open").test(spotRequest("sir-1", "open")));
        assertFalse(SpotRequestPredicates.inAnyOfStates("active", "open").test(spotRequest("sir-1", "closed")));
        assertFalse(SpotRequestPredicates.inAnyOfStates("active", "open").test(spotRequest("sir-1", "cancelled")));
        assertFalse(SpotRequestPredicates.inAnyOfStates("active", "open").test(spotRequest("sir-1", "failed")));
    }

    /**
     * Should not be possible to create a
     * {@link SpotRequestPredicates#inAnyOfStates(String...)} with an illegal
     * {@link SpotInstanceRequest} state.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInAnyOfStatesPredicateWithIllegalState() {
        SpotRequestPredicates.inAnyOfStates("badstate");
    }

    /**
     * Test the {@link SpotRequestPredicates#allInAnyOfStates(String...)}
     * predicate when there is only a single state to match
     * {@link SpotInstanceRequest}s against.
     */
    @Test
    public void testAllInAnyOfStatesPredicateWithSingleMatchingState() {
        // make sure all valid states are recognized
        assertTrue(SpotRequestPredicates.allInAnyOfStates("active").test(spotRequests()));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("active").test(spotRequests("active")));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("active").test(spotRequests("active", "active")));
        assertFalse(SpotRequestPredicates.allInAnyOfStates("active").test(spotRequests("active", "open")));

        assertTrue(SpotRequestPredicates.allInAnyOfStates("closed").test(spotRequests()));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("closed").test(spotRequests("closed")));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("closed").test(spotRequests("closed", "closed")));
        assertFalse(SpotRequestPredicates.allInAnyOfStates("closed").test(spotRequests("closed", "open")));

        assertTrue(SpotRequestPredicates.allInAnyOfStates("cancelled").test(spotRequests()));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("cancelled").test(spotRequests("cancelled")));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("cancelled").test(spotRequests("cancelled", "cancelled")));
        assertFalse(SpotRequestPredicates.allInAnyOfStates("cancelled").test(spotRequests("cancelled", "open")));

        assertTrue(SpotRequestPredicates.allInAnyOfStates("failed").test(spotRequests()));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("failed").test(spotRequests("failed")));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("failed").test(spotRequests("failed", "failed")));
        assertFalse(SpotRequestPredicates.allInAnyOfStates("failed").test(spotRequests("failed", "open")));

        assertTrue(SpotRequestPredicates.allInAnyOfStates("open").test(spotRequests()));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("open").test(spotRequests("open")));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("open").test(spotRequests("open", "open")));
        assertFalse(SpotRequestPredicates.allInAnyOfStates("open").test(spotRequests("open", "cancelled")));
    }

    /**
     * Test the {@link SpotRequestPredicates#allInAnyOfStates(String...)}
     * predicate when there are more than one state to match
     * {@link SpotInstanceRequest}s against.
     */
    @Test
    public void testAllInAnyOfStatesPredicateWithMultipleMatchingStates() {
        assertTrue(SpotRequestPredicates.allInAnyOfStates("cancelled", "failed").test(spotRequests()));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("cancelled", "failed")
                .test(spotRequests("cancelled", "failed")));
        assertTrue(SpotRequestPredicates.allInAnyOfStates("cancelled", "failed")
                .test(spotRequests("cancelled", "failed", "failed", "cancelled")));
        assertFalse(SpotRequestPredicates.allInAnyOfStates("cancelled", "failed")
                .test(spotRequests("cancelled", "active", "failed", "failed", "cancelled")));

        assertTrue(SpotRequestPredicates.allInAnyOfStates("open", "active")
                .test(spotRequests("open", "open", "active", "open")));
        assertFalse(SpotRequestPredicates.allInAnyOfStates("open", "active")
                .test(spotRequests("open", "open", "active", "cancelled")));
        assertFalse(SpotRequestPredicates.allInAnyOfStates("open", "active")
                .test(spotRequests("open", "open", "active", "closed")));
        assertFalse(SpotRequestPredicates.allInAnyOfStates("open", "active")
                .test(spotRequests("open", "open", "active", "failed")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAllInAnyOfStatesPredicateWithIllegalState() {
        SpotRequestPredicates.allInAnyOfStates("badstate");
    }

    private SpotInstanceRequest spotRequest(String id, String state) {
        return new SpotInstanceRequest().withSpotInstanceRequestId(id).withState(state);
    }

    private List<SpotInstanceRequest> spotRequests(String... states) {
        List<SpotInstanceRequest> requests = new ArrayList<>();
        int index = 1;
        for (String state : states) {
            requests.add(new SpotInstanceRequest().withSpotInstanceRequestId("sir-" + index++).withState(state));
        }
        return requests;
    }
}
