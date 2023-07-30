package com.example.ubi.components.synthetic.database.infrastructure;

import com.example.ubi.components.synthetic.network.infrastructure.Network;

import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.rds.AuroraMysqlClusterEngineProps;
import software.amazon.awscdk.services.rds.AuroraMysqlEngineVersion;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseCluster;
import software.amazon.awscdk.services.rds.DatabaseClusterEngine;
import software.amazon.awscdk.services.rds.InstanceProps;
import software.constructs.Construct;

public class Database extends Construct {

    DatabaseCluster cluster =   null;
    public Database(Construct scope, final String id, final Network dbNetwork){
        
        super(scope, id);

        cluster = DatabaseCluster.Builder.create(this, "UbiDatabase")
            .engine(DatabaseClusterEngine.auroraMysql(AuroraMysqlClusterEngineProps.builder().version(AuroraMysqlEngineVersion.VER_3_01_0).build()))
            .credentials(Credentials.fromPassword( getDBUsername(), SecretValue.unsafePlainText(getDBPassword())))
            .instanceProps(InstanceProps.builder()
                // optional , defaults to t3.medium
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MEDIUM))
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_NAT)
                        .build())
                .vpc(dbNetwork.getVpc())
                .build())
        .build();

        cluster.getConnections().allowDefaultPortFrom(Peer.ipv4(dbNetwork.getVpc().getVpcCidrBlock()));
    }

    public String getDBEndpoint(){
        return cluster.getClusterEndpoint().getHostname();
    }

    public String getDBReadEndpoint(){
        return cluster.getClusterReadEndpoint().getHostname();
    }

    public String getDBUsername(){
        return "clusteradmin";
    }

    public String getDBPassword(){
        return "welcome1";
    }

    public DatabaseCluster getDBCluster(){
        return cluster;
    }
}
