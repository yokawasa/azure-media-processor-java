package azuremediaprocessor;

import azuremediaprocessor.Client;
import azuremediaprocessor.Observer;
import azuremediaprocessor.Subject;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.media.MediaContract;
import com.microsoft.windowsazure.services.media.models.Asset;
import com.microsoft.windowsazure.services.media.models.AssetInfo;
import com.microsoft.windowsazure.services.media.models.AssetFile;
import com.microsoft.windowsazure.services.media.models.AssetFileInfo;
import com.microsoft.windowsazure.services.media.models.AccessPolicyInfo;
import com.microsoft.windowsazure.services.media.models.AccessPolicy;
import com.microsoft.windowsazure.services.media.models.AccessPolicyPermission;
import com.microsoft.windowsazure.services.media.models.LocatorInfo;
import com.microsoft.windowsazure.services.media.models.LocatorType;
import com.microsoft.windowsazure.services.media.models.Locator;
import com.microsoft.windowsazure.services.media.models.ListResult;
import com.microsoft.windowsazure.services.media.models.MediaProcessorInfo;
import com.microsoft.windowsazure.services.media.models.Job;
import com.microsoft.windowsazure.services.media.models.JobState;
import com.microsoft.windowsazure.services.media.models.JobInfo;
import com.microsoft.windowsazure.services.media.models.LinkInfo;
import com.microsoft.windowsazure.services.media.models.Task;
import com.microsoft.windowsazure.services.media.models.TaskInfo;
import com.microsoft.windowsazure.services.media.models.ErrorDetail;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;

public class MediaProcessRunner extends Subject implements Runnable {
    private State state;
    private String assetName;
    private String taskParamFile;
    private String outputDir;
    private Boolean downloadFiles;
    private AssetInfo mediaAsset;
    private MediaContract service;

    private String getTaskBody(int inputAssetId, int outputAssetId) {
        return "<taskBody><inputAsset>JobInputAsset(" + inputAssetId
                + ")</inputAsset>" + "<outputAsset>JobOutputAsset("
                + outputAssetId + ")</outputAsset></taskBody>";
    }

    public MediaProcessRunner(
            MediaContract service,
            String assetName,
            String taskParamFile,
            String outputDir,
            Boolean downloadFiles) {
        this.state = new State();
        this.state.setValue("Initiating");
        this.service = service;
        this.assetName = assetName;
        this.taskParamFile = taskParamFile;
        this.outputDir = outputDir;
        this.downloadFiles = downloadFiles;
    }

    public MediaProcessRunner( 
            MediaContract service,
            String assetName,
            String taskParamFile,
            String outputDir,
            Boolean downloadFiles,
            String state,
            int progress) {
        this.state = new State();
        this.service = service;
        this.assetName = assetName;
        this.taskParamFile = taskParamFile;
        this.outputDir = outputDir;
        this.downloadFiles = downloadFiles;
        this.state.setValue(state);
        this.state.setProgress(progress);
    }

    @Override
    public void run() {
        try {
            String config = readFile(taskParamFile);
            if (config == null || config.equals("")) {
                System.err.println("Media Processor Task Param File cannot be empty:" + taskParamFile);
                System.exit(1);
            }

            MediaProcessorInfo mediaProcessor = Client.getMP();

            synchronized (service) {
                ListResult<AssetInfo> assets = service.list(Asset.list());
                for (AssetInfo asset : assets) {
                    if (asset.getName().equals(assetName)) {
                        mediaAsset = asset;
                    }
                }
            }

            // Create a task with the Media Processor
            Task.CreateBatchOperation task = Task.create(
                            mediaProcessor.getId(),getTaskBody(0,0))
                            .setConfiguration(config)
                            .setName(mediaAsset.getName() + "_Processing");

            Job.Creator jobCreator = Job.create()
                            .setName(mediaAsset.getName() + "_Processing")
                            .addInputMediaAsset(mediaAsset.getId())
                            .setPriority(2)
                            .addTaskCreator(task);

            final JobInfo jobInfo;
            final String jobId;
            synchronized (service) {
                jobInfo = service.create(jobCreator);
                jobId = jobInfo.getId();
            }
            checkJobStatus(jobId);

            // Download output asset files only if opted
            if (downloadFiles) {
                downloadProcessedAssetFilesFromJob(jobInfo);
            }
        } catch (Exception e) {
             System.err.println("Exception occured while running media processing job: "
                                        + e.getMessage());
            throw new RuntimeException(e.toString());
        }
    }

    private synchronized String readFile(String filePath) throws IOException {
        String content;
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();
        while (line != null) {
            sb.append(line);
            sb.append(System.lineSeparator());
            line = br.readLine();
        }
        content = sb.toString();
        return content;
    }

    private synchronized void checkJobStatus(String jobId)
                throws ServiceException, InterruptedException {
        while (true) {
            JobInfo jobInfo = service.get(Job.get(jobId));
            JobState jobState = jobInfo.getState();
            LinkInfo<TaskInfo> tasksLink = service.get(Job.get(jobId)).getTasksLink();
            ListResult<TaskInfo> tasks = service.list(Task.list(tasksLink));
            this.state.setValue(jobState.name());
            this.state.setProgress((int)tasks.get(0).getProgress());
            notifyObservers(this.state);
            Thread.sleep(1000);

            if (
                jobState == JobState.Error 
                || jobState == JobState.Finished 
                || jobState == JobState.Canceled
            ) {
                if (jobInfo.getState() == JobState.Error) {
                    for (TaskInfo taskInfo : tasks) {
                        for (ErrorDetail detail : taskInfo.getErrorDetails()) {
                            System.err.println(
                                    String.format("TaskInfo Error: %s (code %s)",
                                                detail.getMessage(), detail.getCode()
                                            )
                                );
                        }
                    }
                }
                break;
            }
        }
    }

    private synchronized void downloadProcessedAssetFilesFromJob(JobInfo jobInfo)
            throws ServiceException, URISyntaxException, FileNotFoundException, StorageException, IOException {

        final ListResult<AssetInfo> outputAssets;
        outputAssets = service.list(Asset.list(jobInfo.getOutputAssetsLink()));
        AssetInfo processedAsset = outputAssets.get(0);
        final AccessPolicyInfo downloadAccessPolicy;
        final LocatorInfo downloadLocator;

        downloadAccessPolicy = service.create(
                            AccessPolicy.create(
                                "Download",
                                15.0,
                                EnumSet.of(AccessPolicyPermission.READ)
                            )
                        );
        downloadLocator = service.create(
                            Locator.create(
                                downloadAccessPolicy.getId(),
                                processedAsset.getId(),
                                LocatorType.SAS
                            )
                        );

        for (AssetFileInfo assetFile : service.list(AssetFile.list(processedAsset.getAssetFilesLink()))) {
            String fileName = assetFile.getName();
            String outFileName=fileName;
            // Rename JobResult file not to overwrite it in the directory where all job output files are to be stored
            if (fileName.equals("JobResult.txt")) {
                outFileName = "JobResult_" + processedAsset.getName();
            }
            String locatorPath = downloadLocator.getPath();
            int startOfSas = locatorPath.indexOf("?");
            String blobPath = locatorPath + fileName;
            if (startOfSas >= 0) {
                blobPath = locatorPath.substring(0, startOfSas) + "/" + fileName + locatorPath.substring(startOfSas);
            }
            URI baseUri = new URI(blobPath);
            CloudBlobClient blobClient = new CloudBlobClient(baseUri);
            String localFileName = this.outputDir + "/" + outFileName;
            CloudBlockBlob sasBlob = new CloudBlockBlob(baseUri);
            File fileTarget = new File(localFileName);
            sasBlob.download(new FileOutputStream(fileTarget));
        }

        service.delete(Locator.delete(downloadLocator.getId()));
        service.delete(AccessPolicy.delete(downloadAccessPolicy.getId()));
    }
}
