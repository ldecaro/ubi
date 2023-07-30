/* (C)2023 */
package com.example.ubi.toolchain.infrastructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.ubi.Constants;
import com.example.ubi.components.synthetic.Ubiquitous;
import com.example.ubi.components.synthetic.UbiquitousDR;

import software.amazon.awscdk.Arn;
import software.amazon.awscdk.ArnComponents;
import software.amazon.awscdk.ArnFormat;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Stage;
import software.amazon.awscdk.pipelines.CodeCommitSourceOptions;
import software.amazon.awscdk.pipelines.CodePipeline;
import software.amazon.awscdk.pipelines.CodePipelineSource;
import software.amazon.awscdk.pipelines.ShellStep;
import software.amazon.awscdk.pipelines.StageDeployment;
import software.amazon.awscdk.pipelines.Step;
import software.amazon.awscdk.services.codecommit.Repository;
import software.amazon.awscdk.services.codedeploy.EcsApplication;
import software.amazon.awscdk.services.codedeploy.EcsDeploymentGroup;
import software.amazon.awscdk.services.codedeploy.EcsDeploymentGroupAttributes;
import software.amazon.awscdk.services.codedeploy.IEcsApplication;
import software.amazon.awscdk.services.codedeploy.IEcsDeploymentConfig;
import software.amazon.awscdk.services.codedeploy.IEcsDeploymentGroup;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitTrigger;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.constructs.Construct;

public class ContinuousDeployment extends Stack {

    private CodePipeline pipeline = null;

    private List<Environment> environments = new ArrayList<>();    

    private ContinuousDeployment(Construct scope, String id, String gitRepoURL, String gitBranch, StackProps props) {

        super(scope, id, props);
        pipeline = createPipeline(gitRepoURL, gitBranch);
    }

    private CodePipeline createPipeline(String repoURL, String branch) {

        CodePipelineSource source = CodePipelineSource.codeCommit(
                Repository.fromRepositoryName(this, "CodeRepository", repoURL),
                branch,
                CodeCommitSourceOptions.builder()
                        .trigger(CodeCommitTrigger.POLL)
                        .build());

        return CodePipeline.Builder.create(this, "Pipeline-" + Constants.APP_NAME)
                .publishAssetsInParallel(Boolean.FALSE)
                .dockerEnabledForSelfMutation(Boolean.TRUE)
                .crossAccountKeys(Boolean.TRUE)
                .synth(ShellStep.Builder.create(Constants.APP_NAME + "-synth")
                        .input(source)
                        .installCommands(Arrays.asList("npm install"))
                        .commands(Arrays.asList("mvn -B clean package", "npx cdk synth"))
                        .build())
                .build();
    }

    private ContinuousDeployment addStage(
            final String stageName,
            final IEcsDeploymentConfig ecsDeploymentConfig,
            final String CIDR,
            final Environment env,
            final Boolean ADD_APPROVAL) {

        environments.add(env);                
        // The stage
        Stage stage = Stage.Builder.create(pipeline, stageName).env(env).build();

        final String SERVICE_NAME = Constants.APP_NAME + "Service-" + stageName;

        Ubiquitous ubi =    new Ubiquitous(
            stage, 
            Constants.APP_NAME+stageName,
            ecsDeploymentConfig,
            CIDR,
            StackProps.builder()
                .stackName(Constants.APP_NAME+"-"+stageName)
                .description(Constants.APP_NAME+"-"+stageName)
                .build());           

        StageDeployment stageDeployment = pipeline.addStage(stage);

        // Configure AWS CodeDeploy
        Step configureCodeDeployStep = ShellStep.Builder.create("ConfigureBlueGreenDeploy")
                .input(pipeline.getCloudAssemblyFileSet())
                .primaryOutputDirectory("codedeploy")
                .commands(Arrays.asList(new String[] {
                    "chmod a+x ./codedeploy/codedeploy_configuration.sh",
                    "./codedeploy/codedeploy_configuration.sh",
                    String.format(
                            "./codedeploy/codedeploy_configuration.sh %s %s %s %s %s %s",
                            env.getAccount(),
                            env.getRegion(),
                            Constants.APP_NAME,
                            stageName,
                            ((Construct) pipeline).getNode().getId(),
                            SERVICE_NAME)
                }))
                .build();

        stageDeployment.addPre(configureCodeDeployStep);

        // Deploy using AWS CodeDeploy
        stageDeployment.addPost(new CodeDeployStep(
                "codeDeploypreprod",
                configureCodeDeployStep.getPrimaryOutput(),
                referenceCodeDeployDeploymentGroup(env, SERVICE_NAME, ecsDeploymentConfig, stageName),
                stageName));
        return this;
    }

    private ContinuousDeployment deployGlobals(){

        if( this.environments == null || this.environments.size() < 2 ){
            throw new RuntimeException("cannot call prevail() before adding at least 2 stages (addStages())");
        }

        //the stages
        Environment env = environments.get(environments.size()-2);
        Environment envDR = environments.get(environments.size()-1);

        //The stage
        Stage deployStage = Stage.Builder.create(pipeline,Constants.APP_NAME+"Globals").env( envDR ).build();
        
        //My Stack
        new UbiquitousDR(
                deployStage, 
                Constants.APP_NAME+"-Globals",
                env,
                envDR,
                StackProps.builder()
                    .stackName(Constants.APP_NAME+"-Globals")
                    .description(Constants.APP_NAME+"-Globals")
                    .build());        
        
        pipeline.addStage(deployStage);
        return this;
    }    

    /**
     * Self-mutating pipelines create a stage named UpdatePipeline.
     * AWS CodeDeploy uses configuration files to work properly and, depending on the use case (cross-account),
     * this files might need to be transferred to the target account. In order to transfer files, the CDK uses
     * an account support stack with the prefix cross-account-support*. That stack needs permission to publish
     * CodeDeploy artifacts in the target account and those permissions need to be associated with the role the
     * UpdatePipeline project uses.
     *
     * In detail, the information about stacks that depend on the current stack is only available at the time the app
     * finishes synthesizing, and by that point, we have already locked-in the permissions, because they are part
     * of the step.
     *
     * So, in order to overcome this limitation, we are changing the role UpdatePipeline uses, allowing the
     * cross-account-support* stack to do file-publishing to the target account.
     *
     * (https://github.com/aws/aws-cdk/pull/24073)
     *
     */
    private void addCodeDeployCrossAccountAssumeRolePermissions(Map<String, Environment> stageNameEnvironment) {

        if (!stageNameEnvironment.isEmpty()) {

            this.pipeline.buildPipeline();
            for (String stage : stageNameEnvironment.keySet()) {

                HashMap<String, String[]> condition = new HashMap<>();
                condition.put(
                        "iam:ResourceTag/aws-cdk:bootstrap-role",
                        new String[] {"image-publishing", "file-publishing", "deploy"});
                pipeline.getSelfMutationProject()
                        .getRole()
                        .addToPrincipalPolicy(PolicyStatement.Builder.create()
                                .actions(Arrays.asList("sts:AssumeRole"))
                                .effect(Effect.ALLOW)
                                .resources(Arrays.asList("arn:*:iam::"
                                        + stageNameEnvironment.get(stage).getAccount() + ":role/*"))
                                .conditions(new HashMap<String, Object>() {
                                    {
                                        put("ForAnyValue:StringEquals", condition);
                                    }
                                })
                                .build());
            }
        }
    }

    private IEcsDeploymentGroup referenceCodeDeployDeploymentGroup(
            final Environment env, final String serviceName, final IEcsDeploymentConfig ecsDeploymentConfig, String stageName) {

        IEcsApplication codeDeployApp = EcsApplication.fromEcsApplicationArn(
                this,
                Constants.APP_NAME + "EcsCodeDeployAppImport-"+stageName,
                Arn.format(ArnComponents.builder()
                        .arnFormat(ArnFormat.COLON_RESOURCE_NAME)
                        .partition("aws")
                        .region(env.getRegion())
                        .service("codedeploy")
                        .account(env.getAccount())
                        .resource("application")
                        .resourceName(serviceName)
                        .build()));

        IEcsDeploymentGroup deploymentGroup = EcsDeploymentGroup.fromEcsDeploymentGroupAttributes(
                this,
                Constants.APP_NAME + "-EcsCodeDeployDG-"+stageName,
                EcsDeploymentGroupAttributes.builder()
                        .deploymentGroupName(serviceName)
                        .application(codeDeployApp)
                        .deploymentConfig(ecsDeploymentConfig)
                        .build());

        return deploymentGroup;
    }

    protected Boolean isSelfMutationEnabled() {
        return pipeline.getSelfMutationEnabled();
    }

    public static final class Builder implements software.amazon.jsii.Builder<software.amazon.awscdk.Stack> {

        private Construct scope;
        private String id;
        private String gitRepoURL;
        private String gitBranch;
        private List<StageConfig> stages = new ArrayList<>();
        private Boolean DEPLOY_GLOBALS = Boolean.FALSE;

        private software.amazon.awscdk.StackProps props;

        public void test() {}

        public Builder setGitRepo(String gitRepoURL) {
            this.gitRepoURL = gitRepoURL;
            return this;
        }

        public Builder setGitBranch(String gitBranch) {
            this.gitBranch = gitBranch;
            return this;
        }

        public Builder addStage(String name, IEcsDeploymentConfig deployConfig, String cidr, Environment env) {
            this.stages.add(new StageConfig(name, deployConfig, cidr, env));
            return this;
        }

        public Builder deployGlobals(){
            this.DEPLOY_GLOBALS = Boolean.TRUE;
            return this;
        }

        public ContinuousDeployment build() {

            Map<String, Environment> stageNameEnvironment = new HashMap<>();

            ContinuousDeployment pipeline = new ContinuousDeployment(
                    this.scope, this.id, this.gitRepoURL, this.gitBranch, this.props != null ? this.props : null);
            String pipelineAccount = pipeline.getAccount();

            for (StageConfig stageConfig : stages) {

                pipeline.addStage(
                        stageConfig.getStageName(),
                        stageConfig.getEcsDeployConfig(),
                        stageConfig.getCidr(),
                        stageConfig.getEnv(),
                        stageConfig.getApproval());

                if (pipeline.isSelfMutationEnabled()
                        && !pipelineAccount.equals(stageConfig.getEnv().getAccount())) {

                    stageNameEnvironment.put(stageConfig.getStageName(), stageConfig.getEnv());
                }
            }
            if( DEPLOY_GLOBALS ){
                pipeline.deployGlobals();
            }
            if (!stageNameEnvironment.isEmpty()) {
                pipeline.addCodeDeployCrossAccountAssumeRolePermissions(stageNameEnvironment);
            }
            return pipeline;
        }

        private static final class StageConfig {

            String name;
            IEcsDeploymentConfig ecsDeploymentConfig;
            Environment env;
            Boolean approval = Boolean.FALSE;
            String cidr;

            private StageConfig(String name, IEcsDeploymentConfig ecsDeploymentConfig, String cidr, Environment env) {
                this.name = name;
                this.ecsDeploymentConfig = ecsDeploymentConfig;
                this.cidr = cidr;
                this.env = env;
            }

            public String getStageName() {
                return name;
            }

            public IEcsDeploymentConfig getEcsDeployConfig() {
                return ecsDeploymentConfig;
            }

            public Environment getEnv() {
                return env;
            }

            public Boolean getApproval() {
                return approval;
            }

            public String getCidr(){
                return cidr;
            }
        }

        /**
         * @return a new instance of {@link Builder}.
         * @param scope Parent of this stack, usually an `App` or a `Stage`, but could be any construct.
         * @param id The construct ID of this stack.
         */
        @software.amazon.jsii.Stability(software.amazon.jsii.Stability.Level.Stable)
        public static Builder create(final software.constructs.Construct scope, final java.lang.String id) {
            return new Builder(scope, id);
        }
        /**
         * @return a new instance of {@link Builder}.
         * @param scope Parent of this stack, usually an `App` or a `Stage`, but could be any construct.
         */
        @software.amazon.jsii.Stability(software.amazon.jsii.Stability.Level.Stable)
        public static Builder create(final software.constructs.Construct scope) {
            return new Builder(scope, null);
        }
        /**
         * @return a new instance of {@link Builder}.
         */
        @software.amazon.jsii.Stability(software.amazon.jsii.Stability.Level.Stable)
        public static Builder create() {
            return new Builder(null, null);
        }

        private Builder(final software.constructs.Construct scope, final java.lang.String id) {
            this.scope = scope;
            this.id = id;
        }

        public Builder stackProperties(StackProps props) {
            this.props = props;
            return this;
        }
    }
}
