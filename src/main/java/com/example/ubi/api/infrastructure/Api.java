package com.example.ubi.api.infrastructure;

import static com.example.Constants.APP_NAME;

import com.example.ubi.compute.infrastructure.ECS;
import com.example.ubi.database.infrastructure.Database;
import com.example.ubi.network.infrastructure.Network;

import software.amazon.awscdk.services.ecr.assets.DockerImageAsset;
import software.constructs.Construct;

public class Api extends Construct {
    
    private String vpcArn = null;
    private String ecsClusterName = null;
    private String ecsTaskRole = null;
    private String ecsTaskExecutionRole = null;
    private String appURL = null;

    private String dbEndpoint = null;
    private String dbEndpointRead = null;
    private String dbUsername = null;
    private String dbPassword = null;
    
    public Api(final Construct scope, final String id, final String deploymentConfig, final String cidr){

        super(scope, id);
        String strEnvType   =   id.split("Api")[id.split("Api").length-1];
        
        DockerImageAsset.Builder.create(this, APP_NAME+"-container")
            .directory("./target")
            .build();

        Network network = new Network(
            this, 
            "Network", 
            APP_NAME,
            cidr);

        ECS ecs = new ECS(
            this, 
            "ECS", 
            deploymentConfig, 
            strEnvType, 
            network); 

        Database db =   new Database(this, "UbiDatabase", network);
        
        this.vpcArn =   network.getVpc().getVpcArn();
        this.ecsClusterName = ecs.getCluster().getClusterName();
        this.ecsTaskRole = ecs.getTaskRole().getRoleName();
        this.ecsTaskExecutionRole = ecs.getTaskExecutionRole().getRoleName();
        this.appURL = "http://"+ecs.getALB().getLoadBalancerDnsName();     

        this.dbEndpoint = db.getDBEndpoint();
        this.dbEndpointRead = db.getDBReadEndpoint();
        this.dbUsername = db.getDBUsername();
        this.dbPassword = db.getDBPassword();
    }

    public String getVpcArn(){
        return this.vpcArn;
    }

    public String getEcsClusterName(){
        return this.ecsClusterName;
    }

    public String getEcsTaskRole(){
        return this.ecsTaskRole;
    }

    public String getEcsTaskExecutionRole(){
        return this.ecsTaskExecutionRole;
    }

    public String getAppURL(){
        return this.appURL;
    }

    public String getDBEndpoint(){
        return this.dbEndpoint;
    }

    public String getDBReadEndpoint(){
        return this.dbEndpointRead;
    }

    public String getDBUsername(){
        return this.dbUsername;
    }

    public String getDBPassword(){
        return this.dbPassword;
    }
}