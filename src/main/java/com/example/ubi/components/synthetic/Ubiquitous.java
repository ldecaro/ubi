package com.example.ubi.components.synthetic;

import java.util.stream.Collectors;

import com.example.ubi.Constants;
import com.example.ubi.components.synthetic.api.infrastructure.Api;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codedeploy.IEcsDeploymentConfig;
import software.amazon.awscdk.services.codedeploy.IEcsDeploymentGroup;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

public class Ubiquitous extends Stack {

    IEcsDeploymentGroup deploymentGroup = null;
    IRole codeDeployRole    =   null;

    public Ubiquitous(Construct scope, String id, IEcsDeploymentConfig deploymentConfig, String cidr, StackProps props ){
        
        super(scope, id, props);

        // String stageName = ((Stage)scope).getStageName();

        String envType = this.getStackName().substring(this.getStackName().indexOf(Constants.APP_NAME)+Constants.APP_NAME.length());
        
        Api example = new Api(
            this, 
            Constants.APP_NAME+"Api"+envType,
            deploymentConfig,
            cidr);

        //exporting properties to configure Route53 for Recovery.
        //ALB
        StringParameter.Builder.create(this, Constants.APP_NAME+"SSM-ALB")
            .parameterName(Constants.SSM_ELB_ENDPOINT)
            .description("The ELB of the application "+Constants.APP_NAME)
            .stringValue( example.getAlbEndpoint() )
            .build();

        StringParameter.Builder.create(this, Constants.APP_NAME+"SSM-ALB-ARN")
            .parameterName(Constants.SSM_ELB_ENDPOINT_ARN)
            .description("The ELB Arn of the application "+Constants.APP_NAME)
            .stringValue( example.getAlbEndpointArn() )
            .build();        
            
        StringParameter.Builder.create(this, Constants.APP_NAME+"SSM-ALB-SG")
            .parameterName(Constants.SSM_ELB_ENDPOINT_SG)
            .description("The SG Id of the application "+Constants.APP_NAME)
            .stringValue( Fn.select(0, example.getAlbSecurityGroups()) )
            .build();                

        StringParameter.Builder.create(this, Constants.APP_NAME+"SSM-ALB-HostedZoneId")
            .parameterName(Constants.SSM_ELB_ENDPOINT_ZONEID)
            .description("The Hosted Zone Id of Elb of the application "+Constants.APP_NAME)
            .stringValue( example.getAlbHostedZoneId() )
            .build();                        

        //Database
        StringParameter.Builder.create(this, Constants.APP_NAME+"SSM-DB")
            .parameterName(Constants.SSM_DB_ENDPOINT)
            .description("The DB of the application "+Constants.APP_NAME)
            .stringValue( example.getDBEndpoint() )
            .build();

        StringParameter.Builder.create(this, Constants.APP_NAME+"SSM-DBREAD")
            .parameterName(Constants.SSM_DB_ENDPOINT_READ)
            .description("The DB Read of the application "+Constants.APP_NAME)
            .stringValue( example.getDBReadEndpoint() )
            .build();

        StringParameter.Builder.create(this, Constants.APP_NAME+"VPC-ID")
            .parameterName(Constants.SSM_VPC_ID)
            .description("The VPC of the application "+Constants.APP_NAME)
            .stringValue( example.getNetwork().getVpc().getVpcId() )
            .build();   

        StringParameter.Builder.create(this, Constants.APP_NAME+"VPC-AZs")
            .parameterName(Constants.SSM_VPC_AZS)
            .description("The VPC AZs of the application "+Constants.APP_NAME)
            .stringValue( example.getNetwork().getVpc().getAvailabilityZones().stream().collect(Collectors.joining(",")) )
            .build();                  
        
            
        CfnOutput.Builder.create(this, "VPC")
            .description("Arn of the VPC ")
            .value(example.getVpcArn())
            .build();

        CfnOutput.Builder.create(this, "ECSCluster")
            .description("Name of the ECS Cluster ")
            .value(example.getEcsClusterName())
            .build();            

        CfnOutput.Builder.create(this, "TaskRole")
            .description("Role name of the Task being executed ")
            .value(example.getEcsTaskRole())
            .build();            

        CfnOutput.Builder.create(this, "ExecutionRole")
            .description("Execution Role name of the Task being executed ")
            .value(example.getEcsTaskExecutionRole())
            .build();              
            
        CfnOutput.Builder.create(this, "ApplicationURL")
            .description("Application is acessible from this url")
            .value(example.getAppURL())
            .build();        
            
        CfnOutput.Builder.create(this, "DBEndpoint")
            .description("Address of the Database Endpoint ")
            .value(example.getDBEndpoint())
            .build();            

        CfnOutput.Builder.create(this, "DBEndpointRead")
            .description("Address of the database Endpoint open for reads")
            .value(example.getDBReadEndpoint())
            .build();            

        CfnOutput.Builder.create(this, "DBUsername")
            .description("DB username")
            .value(example.getDBUsername())
            .build();              
            
        CfnOutput.Builder.create(this, "DBPassword")
            .description("DB password")
            .value(example.getDBPassword())
            .build();              
    }

    public IRole getCodeDeployRole(){
        return this.codeDeployRole;
    }

    public IEcsDeploymentGroup getEcsDeploymentGroup(){
        return this.deploymentGroup;
    }
}