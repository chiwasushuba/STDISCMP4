package edu.animoroll.web;

import edu.animoroll.contracts.AuthReply;
import java.io.Serializable;
import java.time.Instant;

public class PortalSession implements Serializable {
  private final String id;
  private final String name;
  private final String role;
  private String accessToken;
  private String refreshToken;
  private Instant renewAt;

  public PortalSession(AuthReply reply) {
    id = reply.getUserId();
    name = reply.getName();
    role = reply.getRole();
    update(reply);
  }

  public void update(AuthReply reply) {
    accessToken = reply.getAccessToken();
    refreshToken = reply.getRefreshToken();
    renewAt = Instant.now().plusSeconds(840);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getRole() {
    return role;
  }

  public String getFirstName() {
    return name.split(" ")[0];
  }

  public String getInitials() {
    return name.substring(0, 1);
  }

  public String accessToken() {
    return accessToken;
  }

  public String refreshToken() {
    return refreshToken;
  }

  public boolean needsRefresh() {
    return Instant.now().isAfter(renewAt);
  }
}
