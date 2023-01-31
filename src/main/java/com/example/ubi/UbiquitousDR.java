package com.example.ubi;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.example.Constants;

import software.amazon.awscdk.Arn;
import software.amazon.awscdk.ArnComponents;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.SdkCallsPolicyOptions;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcAttributes;
import software.amazon.awscdk.services.route53.CfnHealthCheck;
import software.amazon.awscdk.services.route53.CfnHealthCheck.HealthCheckConfigProperty;
import software.amazon.awscdk.services.route53.CfnHealthCheck.HealthCheckTagProperty;
import software.amazon.awscdk.services.route53.CfnRecordSet;
import software.amazon.awscdk.services.route53.CfnRecordSet.AliasTargetProperty;
import software.amazon.awscdk.services.route53.CnameRecord;
import software.amazon.awscdk.services.route53.PrivateHostedZone;
import software.constructs.Construct;

public class UbiquitousDR extends Stack {


    public UbiquitousDR(Construct scope, String id, Environment primary, Environment secondary, StackProps props){ 

        super(scope, id, props);

        String elbEndpoint = getSSMParameterValue("Elb", primary, Constants.SSM_ELB_ENDPOINT );
        String elbEndpointArn = getSSMParameterValue("ElbArn", primary, Constants.SSM_ELB_ENDPOINT_ARN );
        String elbSecurityGroupId = getSSMParameterValue("ElbSG", primary, Constants.SSM_ELB_ENDPOINT_SG );
        String elbZoneId = getSSMParameterValue("ElbZoneId", primary, Constants.SSM_ELB_ENDPOINT_ZONEID );
        
        String dbEndpoint = getSSMParameterValue("DB", primary, Constants.SSM_DB_ENDPOINT );
        String dbEndpointRead = getSSMParameterValue("DBRead",primary, Constants.SSM_DB_ENDPOINT_READ );
        String vpcId = getSSMParameterValue("VpcId", primary, Constants.SSM_VPC_ID );
        String vpcAzs = getSSMParameterValue("VpcAzs", primary, Constants.SSM_VPC_AZS);
        IVpc vpc =   Vpc.fromVpcAttributes(
            this,
            "VpcIdPrimary", 
            VpcAttributes.builder()
                .vpcId(vpcId)
                .availabilityZones(Arrays.asList(vpcAzs.split(",", -1)))
                .build());

        String elbEndpointDR = getSSMParameterValue("ElbDR", secondary, Constants.SSM_ELB_ENDPOINT );
        String elbEndpointArnDR = getSSMParameterValue("ElbArnDR", secondary, Constants.SSM_ELB_ENDPOINT_ARN );
        String elbSecurityGroupIdDR = getSSMParameterValue("ElbSGDR", secondary, Constants.SSM_ELB_ENDPOINT_SG );
        String elbZoneIdDR = getSSMParameterValue("ElbZoneIdDR", secondary, Constants.SSM_ELB_ENDPOINT_ZONEID );

        String dbEndpointDR = getSSMParameterValue("DBDR", secondary, Constants.SSM_DB_ENDPOINT );     
        String dbEndpointReadDR = getSSMParameterValue("DBReadDR", secondary, Constants.SSM_DB_ENDPOINT_READ );
        String vpcIdDR = getSSMParameterValue("VpcIdDR", secondary, Constants.SSM_VPC_ID );
        String vpcAzsDR = getSSMParameterValue("VpcAzsDR", secondary, Constants.SSM_VPC_AZS);
        IVpc vpcDR =   Vpc.fromVpcAttributes(
            this,
            "VpcIdDr", 
            VpcAttributes.builder()
                .vpcId(vpcIdDR)
                .availabilityZones(Arrays.asList(vpcAzsDR.split(",", -1)))
                .build());
        //TODO need to associate phz with the VPC 1. Currently this is associated with the VPC DR
        //PHZ
        PrivateHostedZone phz = PrivateHostedZone.Builder
            .create(this, Constants.APP_NAME+"PHZ")
            .comment("Private Hosted Zone for "+Constants.APP_NAME)
            .vpc(vpcDR)
            .zoneName(Constants.DOMAIN)
            .build();

        //CNAMES
        createCNAME("db", dbEndpoint, phz);
        createCNAME("db-primary", dbEndpoint, phz);
        createCNAME("db-primary-read", dbEndpointRead, phz);
        createCNAME("db-dr", dbEndpointDR, phz);
        createCNAME("db-dr-read", dbEndpointReadDR, phz);

        //HealthCheck Primary
        Boolean PRIMARY = Boolean.TRUE;

        createFailoverRecordSet(
            Constants.APP_NAME+"ALB", 
            "app."+Constants.DOMAIN, 
            elbEndpoint, 
            elbEndpointArn, 
            elbSecurityGroupId, 
            elbZoneId, 
            phz, 
            PRIMARY);
        createFailoverRecordSet(
            Constants.APP_NAME+"ALBDR", 
            "app."+Constants.DOMAIN, 
            elbEndpointDR, 
            elbEndpointArnDR, 
            elbSecurityGroupIdDR, 
            elbZoneIdDR, 
            phz, 
            !PRIMARY);           
        
        //Add the replication task using DMS
    }

    private CnameRecord createCNAME(String recordName, String endpoint, PrivateHostedZone phz){
        
        return CnameRecord.Builder.create(this, Constants.APP_NAME+"CNAME"+recordName)
            .comment("Endpoint CNAME for App "+Constants.APP_NAME)
            .recordName(recordName)
            .domainName(endpoint)
            .zone(phz)
            .ttl(Duration.seconds(30))
            .build();  
    }

    private CfnRecordSet createFailoverRecordSet(String id, String recordName, String endpoint, String endpointArn, String endpointSGId, String endpointZoneId, PrivateHostedZone phz, Boolean IS_PRIMARY){

        String failover = "PRIMARY";

        if(!IS_PRIMARY){
            failover = "SECONDARY";
        }

        CfnRecordSet recordSet = CfnRecordSet.Builder.create(this,Constants.APP_NAME+id)
            .name(recordName)
            .type("A")
            // the properties below are optional
            .aliasTarget(AliasTargetProperty.builder()
                    .dnsName(endpoint)
                    .hostedZoneId(endpointZoneId)
                    // the properties below are optional
                    .evaluateTargetHealth(true)
                    .build())
            .comment(Constants.APP_NAME+id)
            .failover(failover)
            .healthCheckId(createHealthCheck(id, recordName+"-"+failover, endpoint, 80, "HTTP").getAttrHealthCheckId())
            .hostedZoneId(phz.getHostedZoneId())
            .setIdentifier(Constants.APP_NAME+id)
            .build();

        return recordSet;
    }

    /*
     * A healthcheck that monitors health at each 30s in  for one check every 10s
     */
    private CfnHealthCheck createHealthCheck(final String id, final String name, final String fqdn, final Integer port, final String type){

        CfnHealthCheck cfnHealthCheck = CfnHealthCheck.Builder.create(this, id+"HC")
         .healthCheckConfig(HealthCheckConfigProperty.builder()
                 .type(type)
                 .fullyQualifiedDomainName(fqdn)            
                 .port(port)
                 .requestInterval(10)                 
                 .build())
         .healthCheckTags(Arrays.asList(HealthCheckTagProperty.builder()
                 .key("Name")
                 .value(name)
                 .build()))                 
         .build();

         return cfnHealthCheck;
    }

    private String getSSMParameterValue(final String id, final Environment env, final String parameterName){

        String region = env.getRegion();
        String account = env.getAccount();

        String paramName    =   parameterName;
        if(parameterName.startsWith("/")){
            paramName = parameterName.substring(1);
        }
        final String param = paramName;

        String ssmArn = Arn.format(ArnComponents.builder()
            .partition("aws")
            .region(region)
            .account(account)
            .service("ssm")
            .resource("parameter")
            .resourceName("Service/"+Constants.APP_NAME+"/*")
            .build());      

        AwsCustomResource parameter =  AwsCustomResource.Builder.create(this, Constants.APP_NAME+"CustomResource"+id)
          .onCreate(AwsSdkCall.builder()
            .service("SSM")
            .region(region)
            .action("getParameter")
            .parameters( new HashMap(){{put("Name",parameterName);}})
            .physicalResourceId(PhysicalResourceId.of(new Date().toString()))
            .build())
          .onUpdate(AwsSdkCall.builder()
            .service("SSM")
            .region(region)
            .action("getParameter")
            .parameters( new HashMap(){{put("Name",parameterName);}})
            .physicalResourceId(PhysicalResourceId.of(new Date().toString()))
            .build())
          .policy(AwsCustomResourcePolicy.fromSdkCalls(SdkCallsPolicyOptions.builder().resources(Arrays.asList(ssmArn)).build()))
          .build();    
          
          return parameter.getResponseField("Parameter.Value");
    }
}