# Multi-Region Warm/Standby  using AWS Fargate and RDS with lowest RPO/RTO

The project deploys a multi-region Warm/Standby Java-based microservice using a CI/CD pipeline. The design includes regional active-passive databases with change-data-capture (CDC) for lowest RTO (<15 mins).

![Architecture](imgs/ubi-arch.png)
