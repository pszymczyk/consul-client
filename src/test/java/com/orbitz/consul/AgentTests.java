package com.orbitz.consul;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.orbitz.consul.model.agent.Agent;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.health.HealthCheck;
import com.orbitz.consul.model.health.Service;
import com.orbitz.consul.model.health.ServiceHealth;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class AgentTests extends BaseIntegrationTest {

    @Test
    public void shouldRetrieveAgentInformation() throws UnknownHostException {
        Agent agent = client.agentClient().getAgent();

        assertNotNull(agent);
        assertEquals("127.0.0.1", agent.getConfig().getClientAddr());
    }

    @Test
    public void shouldRegisterTtlCheck() throws UnknownHostException, InterruptedException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, 10000L, serviceName, serviceId);

        Thread.sleep(100);

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(2));
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldRegisterHttpCheck() throws UnknownHostException, InterruptedException, MalformedURLException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, new URL("http://localhost:1337/health"), 1000L, serviceName, serviceId);

        Thread.sleep(100);

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(2));
            }
        }

        assertTrue(found);
    }

    // Need to fix tests for 0.6.2
    // @Test
    // public void shouldRegisterTcpCheck() throws UnknownHostException, InterruptedException, MalformedURLException {
    //     Consul client = Consul.builder().build();
    //     String serviceName = UUID.randomUUID().toString();
    //     String serviceId = UUID.randomUUID().toString();
    //
    //     client.agentClient().register(8080, HostAndPort.fromParts("localhost", 1337), 1000L, serviceName, serviceId);
    //
    //     Thread.sleep(100);
    //
    //     boolean found = false;
    //
    //     for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
    //         if (health.getService().getId().equals(serviceId)) {
    //             found = true;
    //             assertThat(health.getChecks().size(), is(2));
    //         }
    //     }
    //
    //     assertTrue(found);
    //     client.agentClient().deregister(serviceId);
    // }

    @Test
    public void shouldRegisterMultipleChecks() throws UnknownHostException, InterruptedException, MalformedURLException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        List<Registration.RegCheck> regChecks = ImmutableList.of(
                Registration.RegCheck.script("/usr/bin/echo \"sup\"", 10),
                Registration.RegCheck.http("http://localhost:8080/health", 10));

        client.agentClient().register(8080, regChecks, serviceName, serviceId);

        Thread.sleep(100);

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(3));
            }
        }

        assertTrue(found);
    }

    // This is apparently valid
    // to register a single "Check"
    // and multiple "Checks" in one call
    @Test
    public void shouldRegisterMultipleChecks2() throws UnknownHostException, InterruptedException, MalformedURLException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        Registration.RegCheck single= Registration.RegCheck.script("/usr/bin/echo \"sup\"", 10);

        List<Registration.RegCheck> regChecks = ImmutableList.of(
                Registration.RegCheck.http("http://localhost:8080/health", 10));

        Registration reg = ImmutableRegistration.builder()
                .check(single)
                .checks(regChecks)
                .address("localhost")
                .port(8080)
                .name(serviceName)
                .id(serviceId)
                .build();
        client.agentClient().register(reg);

        Thread.sleep(100);

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(3));
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldDeregister() throws UnknownHostException, InterruptedException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, 10000L, serviceName, serviceId);
        client.agentClient().deregister(serviceId);
        Thread.sleep(1000L);
        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
            }
        }

        assertFalse(found);
    }

    @Test
    public void shouldGetChecks() {
        String id = UUID.randomUUID().toString();
        client.agentClient().register(8080, 20L, UUID.randomUUID().toString(), id);

        boolean found = false;

        for (Map.Entry<String, HealthCheck> check : client.agentClient().getChecks().entrySet()) {
            if (check.getValue().getCheckId().equals("service:" + id)) {
                found = true;
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldGetServices() {
        String id = UUID.randomUUID().toString();
        client.agentClient().register(8080, 20L, UUID.randomUUID().toString(), id);

        boolean found = false;

        for (Map.Entry<String, Service> service : client.agentClient().getServices().entrySet()) {
            if (service.getValue().getId().equals(id)) {
                found = true;
            }
            ;
        }

        assertTrue(found);
    }

    @Test
    public void shouldSetWarning() throws UnknownHostException, NotRegisteredException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();
        String note = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId);
        client.agentClient().warn(serviceId, note);

        verifyState("warning", client, serviceId, serviceName, note);
    }

    @Test
    public void shouldSetFailing() throws UnknownHostException, NotRegisteredException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();
        String note = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId);
        client.agentClient().fail(serviceId, note);

        verifyState("critical", client, serviceId, serviceName, note);
    }

    @Test
    public void shouldRegisterNodeScriptCheck() throws InterruptedException {
        String checkId = UUID.randomUUID().toString();

        client.agentClient().registerCheck(checkId, "test-validate", "/usr/bin/echo \"sup\"", 30);

        HealthCheck check = client.agentClient().getChecks().get(checkId);

        assertEquals(check.getCheckId(), checkId);
        assertEquals(check.getName(), "test-validate");
    }


    @Test
    public void shouldRegisterNodeHttpCheck() throws InterruptedException, MalformedURLException {
        String checkId = UUID.randomUUID().toString();

        client.agentClient().registerCheck(checkId, "test-validate", new URL("http://foo.local:1337/check"), 30);

        HealthCheck check = client.agentClient().getChecks().get(checkId);

        assertEquals(check.getCheckId(), checkId);
        assertEquals(check.getName(), "test-validate");
    }

    @Test
    public void shouldRegisterNodeTtlCheck() throws InterruptedException, MalformedURLException {
        String checkId = UUID.randomUUID().toString();

        client.agentClient().registerCheck(checkId, "test-validate", 30);

        HealthCheck check = client.agentClient().getChecks().get(checkId);

        assertEquals(check.getCheckId(), checkId);
        assertEquals(check.getName(), "test-validate");
    }


    private void verifyState(String state, Consul client, String serviceId,
                             String serviceName, String output) throws UnknownHostException {

        Map<String, HealthCheck> checks = client.agentClient().getChecks();
        HealthCheck check = checks.get("service:" + serviceId);

        assertNotNull(check);
        assertEquals(serviceId, check.getServiceId().get());
        assertEquals(serviceName, check.getServiceName().get());
        assertEquals(state, check.getStatus());

        if (output != null) {
            assertEquals(output, check.getOutput().get());
        }
    }
}
