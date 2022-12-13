package com.example.toolchain;

import com.example.App;
import com.example.Constants;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class Toolchain extends Stack {

    public static final String CODECOMMIT_REPO            = Constants.APP_NAME;
    public static final String CODECOMMIT_BRANCH          = "master";

    public static final String COMPONENT_ACCOUNT        =   App.TOOLCHAIN_ACCOUNT;
    public static final String COMPONENT_REGION         =   App.TOOLCHAIN_REGION;
    public static final String COMPONENT_CIDR           =   "10.0.0.0/24";

    public static final String COMPONENT_REGION_DR      =   "us-west-2";
    public static final String COMPONENT_CIDR_DR        =   "10.0.1.0/24";

    public Toolchain(final Construct scope, final String id, final StackProps props) throws Exception {

        super(scope, id, props);           

        Pipeline pipeline = new Pipeline(
            this,
            "BlueGreenPipeline", 
            Toolchain.CODECOMMIT_REPO,
            Toolchain.CODECOMMIT_BRANCH);

        pipeline.addStage(
            "Prod",
            "CodeDeployDefault.ECSLinear10PercentEvery1Minutes",
            Toolchain.COMPONENT_CIDR,
            Toolchain.COMPONENT_ACCOUNT,
            Toolchain.COMPONENT_REGION);

        pipeline.addStage(
            "DR",
            "CodeDeployDefault.ECSLinear10PercentEvery1Minutes",
            Toolchain.COMPONENT_CIDR_DR,
            Toolchain.COMPONENT_ACCOUNT,
            Toolchain.COMPONENT_REGION_DR);            
    }
}