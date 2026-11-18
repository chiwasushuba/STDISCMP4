package edu.northstar.common;

import io.grpc.Context;
import io.grpc.Status;

public record Actor(String id, String name, String role, String sessionId, String token) {
  public static final Context.Key<Actor> CURRENT = Context.key("actor");

  public static Actor current() {
    Actor actor = CURRENT.get();
    if (actor == null)
      throw Status.UNAUTHENTICATED.withDescription("Please sign in again.").asRuntimeException();
    return actor;
  }

  public static Actor require(String role) {
    Actor actor = current();
    if (!role.equals(actor.role()))
      throw Status.PERMISSION_DENIED
          .withDescription("You do not have access to this action.")
          .asRuntimeException();
    return actor;
  }
}
