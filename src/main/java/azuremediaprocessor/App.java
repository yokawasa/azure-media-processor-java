package azuremediaprocessor;

import azuremediaprocessor.Client;
import azuremediaprocessor.PropertyLoader;
import com.microsoft.windowsazure.exception.ServiceException;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Hashtable;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class App 
{

    public static void main( String[] args )
    {
        new App().start(args);
    }

    public void start( String[] args )
    {
        String conffile = null;
        String mptype = null;
        String uploadfile = null;
        String assetname = null;
        String paramfile = null;
        String outputdir = null;

        Options opts = new Options();
        opts.addOption("c", "config", true, "(Required) App config file. ex) app.config");
        opts.addOption("t", "type", true, "(Required) Media Processor type (Integer):\n" 
                                        + "1 -> Media Encoder Standard\n"
                                        + "10 -> Azure Media Indexer\n"
                                        + "11 -> Azure Media Indexer 2 Preview\n"
                                        + "12 -> Azure Media Hyperlapse\n"
                                        + "13 -> Azure Media Face Detector\n"
                                        + "14 -> Azure Media Motion Detector\n"
                                        + "15 -> Azure Media Stabilizer\n"
                                        + "16 -> Azure Media Video Thumbnails\n"
                                        + "17 -> Azure Media OCR\n"
                                );
        opts.addOption("f", "file", true, "(Optional) Uploading file. By specifing this, you start from uploading file");
        opts.addOption("a", "assetname", true, "(Required) Asset Name to process media indexing");
        opts.addOption("p", "params", true, "(Optional) Azure Media Processor Configuration XML/Json file. ex) default-indexer.config");
        opts.addOption("o", "output", true, "(Required) Output directory");
        BasicParser parser = new BasicParser();
        CommandLine cl;
        HelpFormatter help = new HelpFormatter();

        try {
            // parse options
            cl = parser.parse(opts, args);
            // handle server option.
            if ( !cl.hasOption("-c") || !cl.hasOption("-a") 
                    || !cl.hasOption("-t") || !cl.hasOption("-o")) {
                throw new ParseException("");
            }
            // handle interface option.
            conffile = cl.getOptionValue("c");
            mptype = cl.getOptionValue("t");
            uploadfile = cl.getOptionValue("f");
            assetname = cl.getOptionValue("a");
            paramfile = cl.getOptionValue("p");
            outputdir = cl.getOptionValue("o");
            if (conffile == null || assetname == null 
                    || mptype == null || paramfile == null || outputdir == null 
                    || !Constants.MediaProcessorType_MAP.containsKey(mptype) ) {
                throw new ParseException("");
            }

            // handlet destination option.
            System.out.println("Starting application...");
            System.out.println("Config file :" + conffile);
            System.out.println("AMS Account :" + PropertyLoader.getInstance(conffile)
                                                    .getValue("MediaServicesAccountName"));
            if (cl.hasOption("-f")) { 
                System.out.println("Uploading file : " + uploadfile);
            }
            System.out.println("Media Processor : " + Constants.MediaProcessorType_MAP.get(mptype) );
            System.out.println("Asset name : " + assetname);
            System.out.println("Task param file : " + paramfile);
            System.out.println("Output dir : " + outputdir);

        } catch (IOException | ParseException e) {
            help.printHelp("App -c <app.config> [-f <uploadfile>] -a <assetname> -p <amitaskparam.config> -o <outputdir>", opts);
            System.exit(1);
        }
       
        // create client instance 
        Client client = null;

        try {
            client  = new Client(
                        PropertyLoader.getInstance(conffile).getValue("MediaServicesAccountName"),
                        PropertyLoader.getInstance(conffile).getValue("MediaServicesAccountKey")
                    );
        } catch ( IOException e){
            System.err.println("Client initializing failure:" + e);
            System.exit(1);
        }

        // uploading if opted
        if (uploadfile != null ) { 
            try {
                client.UploadFileAndCreateAsset(uploadfile, assetname);
            } catch ( Exception e ){
                System.err.println("Video upload failure:" + e);
                System.exit(1);
            }
        }

        // media processing asset
        try {
            client.RunMediaProcessingJob(
                            Constants.MediaProcessorType_MAP.get(mptype),
                            assetname,
                            paramfile,
                            outputdir);
        } catch ( Exception e ){
            System.err.println("Video indexing failure:" + e);
            System.exit(1);
        }
    }
}
