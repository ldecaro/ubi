package com.example.ubi.database.infrastructure;

import com.example.ubi.network.infrastructure.Network;

import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
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
        .engine(DatabaseClusterEngine.auroraMysql(AuroraMysqlClusterEngineProps.builder().version(AuroraMysqlEngineVersion.VER_2_08_1).build()))
        .credentials(Credentials.fromPassword("clusteradmin", SecretValue.unsafePlainText("welcome1")))
        .instanceProps(InstanceProps.builder()
                // optional , defaults to t3.medium
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.SMALL))
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_NAT)
                        .build())
                .vpc(dbNetwork.getVpc())
                .build())
        .build();        
    }
}
