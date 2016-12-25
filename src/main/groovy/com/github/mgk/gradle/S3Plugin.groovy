package com.github.mgk.gradle

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.event.ProgressListener
import com.amazonaws.event.ProgressEvent
import com.amazonaws.services.s3.transfer.Transfer
import com.amazonaws.services.s3.transfer.TransferManager

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.text.DecimalFormat


class S3Extension {
    String region
    String bucket
}


abstract class S3Task extends DefaultTask {
    @Input
    String bucket

    String getBucket() { bucket ?: project.s3.bucket }

    def getS3Client() {
        def creds = new DefaultAWSCredentialsProviderChain()
        AmazonS3Client s3Client = new AmazonS3Client(creds)
        String region = project.s3.region
        if (region) {
            s3Client.region = Region.getRegion(Regions.fromName(region))
        }
        s3Client
    }
}


class S3Upload extends S3Task {
    @Input
    String key

    @Input
    String file

    @Input
    boolean overwrite = false

    @TaskAction
    def task() {
        if (overwrite || !s3Client.doesObjectExist(bucket, key)) {
            s3Client.putObject(bucket, key, new File(file))
        }
    }
}


class S3Download extends S3Task {
    String key
    String file
    String keyPrefix
    String destDir

    @TaskAction
    def task() {
        TransferManager tm = new TransferManager()
        Transfer transfer

        if (keyPrefix != null) {
            File dir = new File(destDir)
            dir.mkdirs()
            transfer = tm.downloadDirectory(bucket, keyPrefix, dir)
        }
        else {
            File f = new File(file)
            f.parentFile.mkdirs()
            transfer = tm.download(bucket, key, f)
        }

        def listener = new S3Listener()
        listener.transfer = transfer
        transfer.addProgressListener(listener)
        transfer.waitForCompletion()
    }

    class S3Listener implements ProgressListener {
        Transfer transfer

        DecimalFormat df = new DecimalFormat("#0.0")
        public void progressChanged(ProgressEvent e) {
            logger.info("${df.format(transfer.progress.percentTransferred)}%")
        }
    }
}


class S3Plugin implements Plugin<Project> {
    void apply(Project target) {
        target.extensions.create('s3', S3Extension)
    }
}