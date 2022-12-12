package com.example.ubi.network.infrastructure;

import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.constructs.Construct;

public class Network extends Construct {

    Vpc vpc = null;

    /**
     * 
     * @param scope
     * @param id
     * @param appName
     * @param cidr if cidr is null network will use a default cidr of 10.0.0.0/24
     */
    public Network (Construct scope, final String id, final String appName, final String cidr){

        super(scope,id);

        String netAddr = "10.0.0.0/24";

        if( cidr != null ){
            netAddr = cidr;
        }

        Vpc vpc = Vpc.Builder.create(this, appName+"VPC") 
            .maxAzs(2)
            .natGateways(1)
            .enableDnsHostnames(Boolean.TRUE)
            .enableDnsSupport(Boolean.TRUE)
            .cidr(netAddr)     
            .build();
    
        SecurityGroup sg    =   SecurityGroup.Builder.create(this, appName+"Sg")
            .vpc(vpc)
            .allowAllOutbound(Boolean.TRUE)
            .build();

        sg.addIngressRule(Peer.anyIpv4(), Port.allTcp());
        sg.addIngressRule(Peer.anyIpv4(), Port.allUdp());

        this.vpc    =   vpc;
    }
    
    public Vpc getVpc(){
        return this.vpc;
    }
}
