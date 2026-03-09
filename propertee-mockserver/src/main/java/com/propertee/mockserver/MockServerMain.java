package com.propertee.mockserver;

public class MockServerMain {
    public static void main(String[] args) throws Exception {
        MockServerConfig config = MockServerConfig.fromArgs(args);
        System.setProperty("propertee.task.baseDir", new java.io.File(config.dataDir, "tasks").getAbsolutePath());

        final MockAdminServer server = new MockAdminServer(config);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                server.stop();
            }
        }, "propertee-mock-shutdown"));

        System.out.println("ProperTee mock admin server listening on http://" + config.bindAddress + ":" + server.getPort() + "/admin");
        while (true) {
            Thread.sleep(60000L);
        }
    }
}
