package com.example.ubi;

public interface Constants {

    public static final String APP_NAME = "UbiTest";

    public static final String DOMAIN   = "ubitest.com";

    public static final String SSM_ELB_ENDPOINT = "/Service/"+APP_NAME+"/Elb/"+"Endpoint";

    public static final String SSM_ELB_ENDPOINT_ARN = "/Service/"+APP_NAME+"/Elb/"+"EndpointArn";

    public static final String SSM_ELB_ENDPOINT_SG = "/Service/"+APP_NAME+"/Elb/"+"SecurityGroupId";

    public static final String SSM_ELB_ENDPOINT_ZONEID = "/Service/"+APP_NAME+"/Elb/"+"HostedZoneId";

    public static final String SSM_DB_ENDPOINT = "/Service/"+APP_NAME+"/DB/"+"Endpoint";

    public static final String SSM_DB_ENDPOINT_READ = "/Service/"+APP_NAME+"/DB/"+"ReadEndoint";

    public static final String SSM_VPC_ID = "/Service/"+APP_NAME+"/Vpc/Id";    

    public static final String SSM_VPC_AZS = "/Service/"+APP_NAME+"/Vpc/Azs";
}