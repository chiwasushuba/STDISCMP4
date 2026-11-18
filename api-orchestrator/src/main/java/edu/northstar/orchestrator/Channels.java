package edu.northstar.orchestrator;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Channels {
  @Bean(destroyMethod = "shutdown")
  ManagedChannel identityChannel(@Value("${rpc.identity.target}") String target) {
    return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
  }

  @Bean(destroyMethod = "shutdown")
  ManagedChannel courseChannel(@Value("${rpc.course.target}") String target) {
    return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
  }

  @Bean(destroyMethod = "shutdown")
  ManagedChannel gradeChannel(@Value("${rpc.grade.target}") String target) {
    return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
  }
}
