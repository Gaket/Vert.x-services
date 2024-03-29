package com.vyng.vertex.service;

import com.vyng.vertex.utils.Errors;
import com.vyng.vertex.utils.Utils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import okhttp3.*;

import java.io.IOException;

public class RemoveUserService {

    private final static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("VertxHttpServer");
    private static final String REMOVE_USER_DEV_ENDPOINT = Utils.getParam("REMOVE_USER_DEV_ENDPOINT");
    private static final String REMOVE_USER_PROD_ENDPOINT = Utils.getParam("REMOVE_USER_PROD_ENDPOINT");
    private static final String DELETE_TOKEN_ENV = "DELETE_TOKEN";

    private final MongoClient mongoClient;

    public RemoveUserService(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    // This is the first type of serving routing, that passes routingContext inside.
    // That was a bad experiment, don't do it in prod. GetUserInfoService has better example with Futures
    public void deleteUser(String phone, String server, RoutingContext routingContext) {
        LOGGER.info("Deleting a user: " + phone + " on a server: " + server);
        removeUserThroughApi(phone, server, routingContext);

        // TODO: Remove the check for user testing. Return it back afterwards
//        allowedToDelete(phone)
//                .setHandler(asyncresult -> {
//                    if (asyncresult.failed()) {
//                        Errors.error(routingContext, 400, asyncresult.cause());
//                        LOGGER.warning("Couldn't get result from Mongo");
//                        return;
//                    }
//
//                    if (asyncresult.result() == null || asyncresult.result().isEmpty()) {
//                        String errorMsg = "Tried to remove a non-whitelisted number: " + phone;
//                        Errors.error(routingContext, 403, errorMsg);
//                        LOGGER.warning(errorMsg);
//                        return;
//                    }
//                    removeUserThroughApi(phone, server, routingContext);
//                });
    }

    private void removeUserThroughApi(String phone, String server, RoutingContext routingContext) {
        OkHttpClient client = new OkHttpClient();
        String endpoint = "prod".equals(server) ? REMOVE_USER_PROD_ENDPOINT : REMOVE_USER_DEV_ENDPOINT;
        Request request = new Request.Builder().url(endpoint + phone)
                .addHeader("x-auth-token", Utils.getParam(DELETE_TOKEN_ENV)).delete().build();
        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onResponse(Call arg0, Response response) throws IOException {
                if (response.isSuccessful()) {
                    LOGGER.info("Deleted the user: " + phone);
                    routingContext.response().setStatusCode(204).end();
                } else {
                    String msg = "Failed to delete the user: " + phone;
                    LOGGER.warning(msg + ". " + response.code());
                    Errors.error(routingContext, response.code(), msg);
                }
            }

            @Override
            public void onFailure(Call arg0, IOException arg1) {
                routingContext.fail(arg1);
            }
        });
    }

    private Future<JsonObject> allowedToDelete(String phone) {
        Promise<JsonObject> result = Promise.promise();
        mongoClient.findOne("phones_to_delete", new JsonObject().put("phone", phone), null, result);
        return result.future();
    }
}