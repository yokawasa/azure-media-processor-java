# Azure Media Processors Java Client Implementation

This repository contains Java codes that implement Azure Media Processors Client using Azure SDK for Java. The documentation for this project will be updated shortly

azure-media-processor-java currently supports only Media analytics type of processors:

 * Azure Media Indexer 2 Preview
 * Azure Media Video Thumbnails
 * Azure Media Stabilizer 
 * Azure Media Motion Detector
 * Azure Media Face Detector
 * Azure Media Hyperlapse
 * Azure Media Indexer 

But it has plan to support the following processors as well in near future:

 * Media Encoder Standard
 * Azure Media Encoder
 * Storage Decryption
 * Windows Azure Media Encoder
 * Windows Azure Media Encryptor
 * Windows Azure Media Packager


## Application Usages
Here is how to execute the application using mvn command:

    mvn exec:java -Dexec.args="-t ProcMode -a YourAssetName -c ./app.config -f ./sample.mp4 -p ./default-facedetection.json -o /path/output"

Here are args for the application that you specify in running the app:

    usage: App -c <app.config> [-f <uploadfile>] -a <assetname> -p
            <amitaskparam.config> -o <outputdir>
    -a,--assetname <arg>   (Required) Asset Name to process media indexing
    -c,--config <arg>      (Required) App config file. ex) app.config
    -f,--file <arg>        (Optional) Uploading file. By specifing this, you
                            start from uploading file
    -o,--output <arg>      (Required) Output directory
    -p,--params <arg>      (Optional) Azure Media Processor Configuration
                            XML/Json file. ex) default-indexer.config
    -t,--type <arg>        (Required) Media Processor type (Integer):
                            10 -> Azure Media Indexer
                            11 -> Azure Media Indexer 2 Preview
                            12 -> Azure Media Hyperlapse
                            13 -> Azure Media Face Detector
                            14 -> Azure Media Motion Detector
                            15 -> Azure Media Stabilizer
                            16 -> Azure Media Video Thumbnails

