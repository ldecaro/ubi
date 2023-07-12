package com.example.ubi.monitoring.infrastructure;

import java.util.Map;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.ses.actions.Lambda;
import software.amazon.awscdk.services.sns.ITopicSubscription;
import software.amazon.awscdk.services.sns.Subscription;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.TopicSubscriptionConfig;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
import software.amazon.awscdk.services.sns.subscriptions.LambdaSubscription;
import software.amazon.awscdk.services.synthetics.alpha.Canary;
import software.amazon.awscdk.services.synthetics.alpha.Code;
import software.amazon.awscdk.services.synthetics.alpha.CustomTestOptions;
import software.amazon.awscdk.services.synthetics.alpha.Runtime;
import software.amazon.awscdk.services.synthetics.alpha.Schedule;
import software.amazon.awscdk.services.synthetics.alpha.Test;
import software.constructs.Construct;
import java.util.HashMap;

public class Monitor extends Construct {
    
    public Monitor(Construct scope, String id, String appURL){

        super(scope, id);

        Map<String,String> envVars =   new HashMap<>();
        envVars.put("url", appURL);

        Canary canary = Canary.Builder.create(this, "MyCanary")
        .schedule(Schedule.rate(Duration.minutes(1)))
        .test(Test.custom(CustomTestOptions.builder()
                .code(Code.fromAsset(getPath()+"../canary"))
                .handler("index.handler")
                .build()))
        .runtime(Runtime.SYNTHETICS_NODEJS_PUPPETEER_4_0)
        .enableAutoDeleteLambdas(Boolean.TRUE)
        .environmentVariables( envVars )
        .build();


        //Creating an alarm that will trigger a topic
        Alarm alarm = Alarm.Builder.create(this, "CanaryAlarm")
            .metric(canary.metricSuccessPercent())
            .evaluationPeriods(2)
            .threshold(90)
            .comparisonOperator(ComparisonOperator.LESS_THAN_THRESHOLD)
            .build();

        Topic topic = Topic.Builder.create(this, "CloudWathSynthetics-CanaryAlarm")
            .displayName("cloudwatch-synthetics-canary-alarm-topic")
            .build();

        topic.addSubscription(new EmailSubscription("lddecaro@amazon.com"));
        // topic.addSubscription(new LambdaSubscription());
        alarm.addAlarmAction(new SnsAction(topic)); 

    }

    private String getPath(){

        String path = "./target/classes/";
        path += this.getClass().getName().substring(0, this.getClass().getName().lastIndexOf(".")).replace(".", "/");
        path += "/";

        return path;
    }    
}
