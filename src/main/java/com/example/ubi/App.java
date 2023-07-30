package com.example.ubi;

import java.util.HashMap;
import java.util.Map;

import com.example.ubi.components.synthetic.Ubiquitous;
import com.example.ubi.components.synthetic.UbiquitousDR;

import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codedeploy.EcsDeploymentConfig;
import software.constructs.Construct;

/**
 * The application includes a Toolchain and an AWS CodeDeploy 
 * bootstrap stacks. The Toolchain creates a BlueGreen pipeline
 * that builds and deploys the Example component into multiple 
 * environments using AWS CodePipeline, AWS CodeBuild and 
 * AWS CodeDeploy. 
 * 
 * The BlueGreen pipeline supports the single-account and 
 * cross-account deployment models.
 * 
 * See prerequisites (README.md) before running the application.
 */
public class App extends software.amazon.awscdk.App {

    public static final String TOOLCHAIN_ACCOUNT = "587929909912";
    public static final String TOOLCHAIN_REGION = "us-west-2";

    // CodeCommit account is the same as the toolchain account
    public static final String CODECOMMIT_REPO = Constants.APP_NAME+"Service";
    public static final String CODECOMMIT_BRANCH = "master";

    public static final String SERVICE_ACCOUNT = App.TOOLCHAIN_ACCOUNT;
    public static final String SERVICE_REGION = TOOLCHAIN_REGION;  
    public static final String SERVICE_CIDR = "10.0.0.0/24"; 
    
    public static final String SERVICE_REGION_DR      =   "us-west-1";
    public static final String SERVICE_CIDR_DR        =   "10.0.1.0/24";

    public static void main(String args[]) throws Exception {

        App app = new App();

        // ContinuousDeployment.Builder.create(app, Constants.APP_NAME+"Toolchain")
        // .stackProperties(StackProps.builder()
        //         .env(Environment.builder()
        //                 .account(App.TOOLCHAIN_ACCOUNT)
        //                 .region(App.TOOLCHAIN_REGION)
        //                 .build())
        //         .build())
        // .setGitRepo(App.CODECOMMIT_REPO)
        // .setGitBranch(App.CODECOMMIT_BRANCH)
        // .addStage(
        //         "Prod",
        //         EcsDeploymentConfig.CANARY_10_PERCENT_5_MINUTES,
        //         App.SERVICE_CIDR,
        //         Environment.builder()
        //                 .account(App.SERVICE_ACCOUNT)
        //                 .region(App.SERVICE_REGION)
        //                 .build())
        // .addStage(
        //         "DR",
        //         EcsDeploymentConfig.CANARY_10_PERCENT_5_MINUTES,
        //         App.SERVICE_CIDR_DR,
        //         Environment.builder()
        //                 .account(App.SERVICE_ACCOUNT)
        //                 .region(App.SERVICE_REGION_DR)
        //                 .build())   
        // .deployGlobals()
        // .build();    
        
        Map<String,String> regionCIDR = new HashMap<>();
        regionCIDR.put(App.SERVICE_REGION, App.SERVICE_CIDR);
        regionCIDR.put(App.SERVICE_REGION_DR, App.SERVICE_CIDR_DR);

        for(String region: regionCIDR.keySet()){

                new Ubiquitous(
                (Construct)app,
                Constants.APP_NAME+"-"+region,
                EcsDeploymentConfig.CANARY_10_PERCENT_5_MINUTES,
                regionCIDR.get(region),
                StackProps.builder()
                        .stackName(Constants.APP_NAME+"-"+region)
                        .description(Constants.APP_NAME+"-"+region)
                        .env(Environment.builder().account("587929909912").region(region).build())
                        .build());  
        }
               
        new UbiquitousDR(
                app, 
                Constants.APP_NAME+"-Globals",
                Environment.builder().account(App.SERVICE_ACCOUNT).region(App.SERVICE_REGION).build(),
                Environment.builder().account(App.SERVICE_ACCOUNT).region(App.SERVICE_REGION_DR).build(),
                StackProps.builder()
                    .stackName(Constants.APP_NAME+"-Globals")
                    .description(Constants.APP_NAME+"-Globals")
                    .env(Environment.builder().account("587929909912").region(App.SERVICE_REGION_DR).build())
                    .build());   

        app.synth();
    }

}