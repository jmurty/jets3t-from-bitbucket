/*
 * JetS3t : Java S3 Toolkit
 * Project hosted at http://bitbucket.org/jmurty/jets3t/
 *
 * Copyright 2006-2010 James Murty
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jets3t.tests;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.jets3t.service.Constants;
import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.S3Service;
import org.jets3t.service.ServiceException;
import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.acl.GrantAndPermission;
import org.jets3t.service.acl.GroupGrantee;
import org.jets3t.service.acl.Permission;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.impl.rest.httpclient.RestStorageService;
import org.jets3t.service.model.S3BucketLoggingStatus;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.model.StorageBucket;
import org.jets3t.service.model.StorageObject;
import org.jets3t.service.security.AWSCredentials;
import org.jets3t.service.security.ProviderCredentials;
import org.jets3t.service.utils.FileComparer;
import org.jets3t.service.utils.FileComparerResults;
import org.jets3t.service.utils.RestUtils;

/**
 * Test the RestS3Service against the S3 endpoint, and apply tests specific to S3.
 *
 * @author James Murty
 */
public class TestRestS3Service extends TestRestS3ServiceToGoogleStorage {

    public TestRestS3Service() throws Exception {
        super();
    }

    @Override
    protected String getTargetService() {
        return TARGET_SERVICE_S3;
    }

    @Override
    protected ProviderCredentials getCredentials() {
        return new AWSCredentials(
            testProperties.getProperty("aws.accesskey"),
            testProperties.getProperty("aws.secretkey"));
    }

    @Override
    protected RestStorageService getStorageService(ProviderCredentials credentials) throws ServiceException {
        Jets3tProperties properties = new Jets3tProperties();
        properties.setProperty("s3service.s3-endpoint", Constants.S3_DEFAULT_HOSTNAME);
        return new RestS3Service(credentials, null, null, properties);
    }

    public void testBucketLogging() throws Exception {
        S3Service s3Service = (S3Service) getStorageService(getCredentials());
        StorageBucket bucket = createBucketForTest("testBucketLogging");
        String bucketName = bucket.getName();

        try {
            // Check logging status is false
            S3BucketLoggingStatus loggingStatus = s3Service.getBucketLoggingStatus(bucket.getName());
            assertFalse("Expected logging to be disabled for bucket " + bucketName,
                loggingStatus.isLoggingEnabled());

            // Enable logging (non-existent target bucket)
            try {
                S3BucketLoggingStatus newLoggingStatus = new S3BucketLoggingStatus(
                    getCredentials().getAccessKey() + ".NonExistentBucketName", "access-log-");
                s3Service.setBucketLoggingStatus(bucket.getName(), newLoggingStatus, true);
                fail("Using non-existent target bucket should have caused an exception");
            } catch (Exception e) {
            }

            // Enable logging (in same bucket)
            S3BucketLoggingStatus newLoggingStatus = new S3BucketLoggingStatus(bucketName, "access-log-");
            s3Service.setBucketLoggingStatus(bucket.getName(), newLoggingStatus, true);
            loggingStatus = s3Service.getBucketLoggingStatus(bucket.getName());
            assertTrue("Expected logging to be enabled for bucket " + bucketName,
                loggingStatus.isLoggingEnabled());
            assertEquals("Target bucket", bucketName, loggingStatus.getTargetBucketName());
            assertEquals("Log file prefix", "access-log-", loggingStatus.getLogfilePrefix());

            // Add TargetGrants ACLs for log files
            newLoggingStatus.addTargetGrant(new GrantAndPermission(
                GroupGrantee.ALL_USERS, Permission.PERMISSION_READ));
            newLoggingStatus.addTargetGrant(new GrantAndPermission(
                GroupGrantee.AUTHENTICATED_USERS, Permission.PERMISSION_READ_ACP));
            s3Service.setBucketLoggingStatus(bucket.getName(), newLoggingStatus, false);
            // Retrieve and verify TargetGrants
            loggingStatus = s3Service.getBucketLoggingStatus(bucket.getName());
            assertEquals(2, loggingStatus.getTargetGrants().length);
            GrantAndPermission gap = loggingStatus.getTargetGrants()[0];
            assertEquals(gap.getGrantee().getIdentifier(), GroupGrantee.ALL_USERS.getIdentifier());
            assertEquals(gap.getPermission(), Permission.PERMISSION_READ);
            gap = loggingStatus.getTargetGrants()[1];
            assertEquals(gap.getGrantee().getIdentifier(), GroupGrantee.AUTHENTICATED_USERS.getIdentifier());
            assertEquals(gap.getPermission(), Permission.PERMISSION_READ_ACP);

            // Disable logging
            newLoggingStatus = new S3BucketLoggingStatus();
            s3Service.setBucketLoggingStatus(bucket.getName(), newLoggingStatus, true);
            loggingStatus = s3Service.getBucketLoggingStatus(bucket.getName());
            assertFalse("Expected logging to be disabled for bucket " + bucketName,
                loggingStatus.isLoggingEnabled());
        } finally {
            cleanupBucketForTest("testBucketLogging");
        }
    }

    public void testUrlSigning() throws Exception {
        RestS3Service service = (RestS3Service) getStorageService(getCredentials());
        StorageBucket bucket = createBucketForTest("testUrlSigning");
        String bucketName = bucket.getName();

        try {
            // Create test object, with private ACL
            String dataString = "Text for the URL Signing test object...";
            S3Object object = new S3Object("Testing URL Signing", dataString);
            object.setContentType("text/html");
            object.addMetadata(service.getRestMetadataPrefix() + "example-header", "example-value");
            object.setAcl(AccessControlList.REST_CANNED_PRIVATE);

            // Determine what the time will be in 5 minutes.
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, 5);
            Date expiryDate = cal.getTime();

            // Create a signed HTTP PUT URL.
            String signedPutUrl = service.createSignedPutUrl(bucket.getName(), object.getKey(),
                object.getMetadataMap(), expiryDate, false);

            // Put the object in S3 using the signed URL (no AWS credentials required)
            RestS3Service restS3Service = new RestS3Service(null);
            restS3Service.putObjectWithSignedUrl(signedPutUrl, object);

            // Ensure the object was created.
            StorageObject objects[] = service.listObjects(bucketName, object.getKey(), null);
            assertEquals("Signed PUT URL failed to put/create object", objects.length, 1);

            // Change the object's content-type and ensure the signed PUT URL disallows the put.
            object.setContentType("application/octet-stream");
            try {
                restS3Service.putObjectWithSignedUrl(signedPutUrl, object);
                fail("Should not be able to use a signed URL for an object with a changed content-type");
            } catch (ServiceException e) {
                object.setContentType("text/html");
            }

            // Add an object header and ensure the signed PUT URL disallows the put.
            object.addMetadata(service.getRestMetadataPrefix() + "example-header-2", "example-value");
            try {
                restS3Service.putObjectWithSignedUrl(signedPutUrl, object);
                fail("Should not be able to use a signed URL for an object with changed metadata");
            } catch (ServiceException e) {
                object.removeMetadata(service.getRestMetadataPrefix() + "example-header-2");
            }

            // Change the object's name and ensure the signed PUT URL uses the signed name, not the object name.
            String originalName = object.getKey();
            object.setKey("Testing URL Signing 2");
            object.setDataInputStream(new ByteArrayInputStream(dataString.getBytes()));
            object = restS3Service.putObjectWithSignedUrl(signedPutUrl, object);
            assertEquals("Ensure returned object key is renamed based on signed PUT URL",
                originalName, object.getKey());

            // Test last-resort MD5 sanity-check for uploaded object when ETag is missing.
            S3Object objectWithoutETag = new S3Object("Object Without ETag");
            objectWithoutETag.setContentType("text/html");
            String objectWithoutETagSignedPutURL = service.createSignedPutUrl(
                bucket.getName(), objectWithoutETag.getKey(), objectWithoutETag.getMetadataMap(),
                expiryDate, false);
            objectWithoutETag.setDataInputStream(new ByteArrayInputStream(dataString.getBytes()));
            objectWithoutETag.setContentLength(dataString.getBytes().length);
            restS3Service.putObjectWithSignedUrl(objectWithoutETagSignedPutURL, objectWithoutETag);
            service.deleteObject(bucketName, objectWithoutETag.getKey());

            // Ensure we can't get the object with a normal URL.
            String s3Url = "https://s3.amazonaws.com";
            URL url = new URL(s3Url + "/" + bucket.getName() + "/" + RestUtils.encodeUrlString(object.getKey()));
            assertEquals("Expected denied access (403) error", 403, ((HttpURLConnection) url
                .openConnection()).getResponseCode());

            // Create a signed HTTP GET URL.
            String signedGetUrl = service.createSignedGetUrl(bucket.getName(), object.getKey(),
                expiryDate, false);

            // Ensure the signed URL can retrieve the object.
            url = new URL(signedGetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            assertEquals("Expected signed GET URL ("+ signedGetUrl +") to retrieve object with response code 200",
                200, conn.getResponseCode());

            // Sanity check the data in the S3 object.
            String objectData = (new BufferedReader(
                new InputStreamReader(conn.getInputStream())))
                .readLine();
            assertEquals("Unexpected data content in S3 object", dataString, objectData);

            // Clean up.
            service.deleteObject(bucketName, object.getKey());
        } finally {
            cleanupBucketForTest("testUrlSigning");
        }
    }

}
