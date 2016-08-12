package azuremediaprocessor;

import azuremediaprocessor.MediaProcessRunner;
import azuremediaprocessor.StateListener;
import azuremediaprocessor.Constants;
import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.media.MediaConfiguration;
import com.microsoft.windowsazure.services.media.MediaContract;
import com.microsoft.windowsazure.services.media.MediaService;
import com.microsoft.windowsazure.services.media.WritableBlobContainerContract;
import com.microsoft.windowsazure.services.media.models.Asset;
import com.microsoft.windowsazure.services.media.models.AssetFile;
import com.microsoft.windowsazure.services.media.models.AssetOption;
import com.microsoft.windowsazure.services.media.models.AssetInfo;
import com.microsoft.windowsazure.services.media.models.AccessPolicyInfo;
import com.microsoft.windowsazure.services.media.models.AccessPolicy;
import com.microsoft.windowsazure.services.media.models.AccessPolicyPermission;
import com.microsoft.windowsazure.services.media.models.LocatorInfo;
import com.microsoft.windowsazure.services.media.models.LocatorType;
import com.microsoft.windowsazure.services.media.models.Locator;
import com.microsoft.windowsazure.services.media.models.ListResult;
import com.microsoft.windowsazure.services.media.models.MediaProcessorInfo;
import com.microsoft.windowsazure.services.media.models.MediaProcessor;
import com.microsoft.windowsazure.services.blob.models.BlockList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream; 
import java.io.ByteArrayInputStream;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.UUID;

public class Client {
    private MediaContract service;
    private static MediaProcessorInfo myMP;

    public Client(String amsaccount, String amskey) {

        this.service = MediaService.create(
                    MediaConfiguration.configureWithOAuthAuthentication(
                        Constants.MEDIA_SERVICE_URI,
                        Constants.OAUTH_URI,
                        amsaccount,
                        amskey,
                        Constants.URN
                    )
            );
    }

    public MediaContract getService() {
        return this.service;
    }

    public static MediaProcessorInfo getMP() {
        return myMP;
    }

    public void UploadFileAndCreateAsset(String uploadFile, String assetName)
            throws ServiceException,FileNotFoundException,NoSuchAlgorithmException,IOException {
        AssetInfo inputAsset = service.create(
                        Asset.create().
                            setName(assetName).
                            setOptions(AssetOption.None)
                     );
        AccessPolicyInfo writable = service.create(
                        AccessPolicy.create(
                            "writable",
                            10,
                            EnumSet.of(AccessPolicyPermission.WRITE)
                        )
                     );
        LocatorInfo assetBlobStorageLocator = service.create(
                        Locator.create(
                            writable.getId(),
                            inputAsset.getId(),
                            LocatorType.SAS
                        )
                     );
    
        WritableBlobContainerContract writer
                    = service.createBlobWriter(assetBlobStorageLocator);
        File mediaFile = new File(uploadFile);
        String fileName = mediaFile.getName();
        InputStream mediaFileInputStream = new FileInputStream(mediaFile);
        String blobName = fileName;
        
        // Upload the local file to the asset
        writer.createBlockBlob(fileName, null);
        String blockId;
        byte[] buffer = new byte[1024000];
        BlockList blockList = new BlockList();
        int bytesRead;
        ByteArrayInputStream byteArrayInputStream;
        while ((bytesRead = mediaFileInputStream.read(buffer)) > 0)
        {
            blockId = UUID.randomUUID().toString();
            byteArrayInputStream = new ByteArrayInputStream(buffer, 0, bytesRead); 
            writer.createBlobBlock(blobName, blockId, byteArrayInputStream);
            blockList.addUncommittedEntry(blockId);
        }
        writer.commitBlobBlocks(blobName, blockList);
        service.action(AssetFile.createFileInfos(inputAsset.getId()));
    }

    public void PrintMediaProcessorsList() 
                    throws InterruptedException, ServiceException {
        ListResult<MediaProcessorInfo> mpis
                = this.service.list( MediaProcessor.list());
        for (MediaProcessorInfo info : mpis) {
            System.out.println("MediaProcessor: " + info.getName() + " Version: " + info.getVersion());
        }
    }

    public void RunMediaProcessingJob( String mediaProcessorName,
                                        String assetName,
                                        String taskParamFile,
                                        String outputDir )
                                            throws InterruptedException, ServiceException {
        this.RunMediaProcessingJob(mediaProcessorName, assetName, taskParamFile, outputDir, true);
    } 

    public void RunMediaProcessingJob( String mediaProcessorName,
                                        String assetName,
                                        String taskParamFile,
                                        String outputDir,
                                        Boolean downloadFiles ) 
                                            throws InterruptedException, ServiceException {
        // Use latest media processor
        ListResult<MediaProcessorInfo> mpis
                = this.service.list(
                    MediaProcessor.list().set("$filter", 
                                   String.format("Name eq '%s'", mediaProcessorName))
                    //MediaProcessor.list().set("$filter", "Name eq 'Azure Media Indexer'")
                               );

        for (MediaProcessorInfo info : mpis) {
            if (myMP == null
                    || info.getVersion().compareTo(myMP.getVersion()) > 0 
            ) {
                myMP = info;
            }
        }
        System.out.println("Using MediaProcessor: " + myMP.getName() + " " + myMP.getVersion());

        final StateListener listener = new StateListener(assetName);
        MediaProcessRunner procRunner = new MediaProcessRunner(
                                    this.service,
                                    assetName,
                                    taskParamFile,
                                    outputDir,
                                    downloadFiles);

        procRunner.addObserver(listener);

        Thread procThread = new Thread(procRunner);

        procThread.setUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    public void uncaughtException(Thread t, Throwable e) {}
                }
            );
        procThread.start();
        PrintStateProgress(listener);
    }
    
    private void PrintStateProgress(StateListener listener)
                                                throws InterruptedException {
        while(true) {
            if (listener.state.getValue().equals("Finished") 
                    || listener.state.getValue().equals("Error") 
                    || listener.state.getValue().equals("Canceled")
            ) {
                break;
            }
            String statusOutput = String.format("MAProcessor: %s [%s] Progress %d Percent",
                                    listener.name,
                                    listener.state.getValue(),
                                    listener.state.getProgress());
            System.out.println(statusOutput);
            Thread.sleep(2000);
        }
    }
}
