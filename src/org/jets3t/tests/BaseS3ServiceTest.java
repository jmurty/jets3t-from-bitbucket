/*
 * jets3t : Java Extra-Tasty S3 Toolkit (for Amazon S3 online storage service)
 * This is a java.net project, see https://jets3t.dev.java.net/
 * 
 * Copyright 2006 James Murty
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;

import junit.framework.TestCase;

import org.jets3t.service.S3Service;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.acl.GroupGrantee;
import org.jets3t.service.acl.Permission;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.model.S3Owner;
import org.jets3t.service.security.AWSCredentials;
import org.jets3t.service.utils.FileComparer;
import org.jets3t.service.utils.Mimetypes;
import org.jets3t.service.utils.ServiceUtils;

public abstract class BaseS3ServiceTest extends TestCase {
    protected AWSCredentials awsCredentials = null;
    
    public BaseS3ServiceTest() throws IOException {
        InputStream propertiesIS = 
            ClassLoader.getSystemResourceAsStream("test.properties");
        
        Properties testProperties = new Properties();        
        testProperties.load(propertiesIS);
        awsCredentials = new AWSCredentials(
            testProperties.getProperty("aws.accesskey"),
            testProperties.getProperty("aws.secretkey"));
    }
    
    protected abstract S3Service getS3Service(AWSCredentials awsCredentials) throws S3ServiceException;
            
    public void testObtainAnonymousServices() throws Exception {
        getS3Service(null);
    }

    public void testListBucketsWithoutCredentials() throws Exception {
        try {
            getS3Service(null).listAllBuckets();
            fail("Bucket listing should fail without authentication");
        } catch (S3ServiceException e) {
        }
    }

    public void testListBucketsWithCredentials() throws Exception {
        getS3Service(awsCredentials).listAllBuckets();
    }

    public void testBucketManagement() throws Exception {
        S3Service s3Service = getS3Service(awsCredentials);

        try {
            s3Service.createBucket(new S3Bucket());
            fail("Cannot create a bucket with name unset");
        } catch (S3ServiceException e) {
        }

        try {
            s3Service.createBucket("");
            fail("Cannot create a bucket with empty name");
        } catch (S3ServiceException e) {
        }

        try {
            s3Service.createBucket("test");
            fail("Cannot create a bucket with non-unique name");
        } catch (S3ServiceException e) {
        }

        String bucketName = awsCredentials.getAccessKey() + ".S3ServiceTest";
        s3Service.createBucket(bucketName);

        boolean bucketExists = s3Service.isBucketAvailable(bucketName);
        assertTrue("Bucket should exist", bucketExists);

        try {
            s3Service.deleteBucket(null);
            fail("Cannot delete a bucket with name null");
        } catch (S3ServiceException e) {
        }

        try {
            s3Service.deleteBucket("");
            fail("Cannot delete a bucket with empty name");
        } catch (S3ServiceException e) {
        }

        try {
            s3Service.deleteBucket("test");
            fail("Cannot delete a bucket you don't own");
        } catch (S3ServiceException e) {
        }

        s3Service.deleteBucket(bucketName);
    }

    public void testObjectManagement() throws Exception {
        S3Service s3Service = getS3Service(awsCredentials);

        String bucketName = awsCredentials.getAccessKey() + ".S3ServiceTest";

        S3Bucket bucket = s3Service.createBucket(bucketName);
        S3Object object = new S3Object();
        object.setKey("TestObject");

        try {
            s3Service.putObject(null, null);
            fail("Cannot create an object without a valid bucket");
        } catch (S3ServiceException e) {
        }

        try {
            s3Service.putObject(null, object);
            fail("Cannot create an object without a valid bucket");
        } catch (S3ServiceException e) {
        }

        try {
            s3Service.putObject(bucket, new S3Object());
            fail("Cannot create an object without a valid object");
        } catch (S3ServiceException e) {
        }

        // Create basic object with default content type and no data.
        S3Object basicObject = s3Service.putObject(bucket, object);
        assertEquals("Unexpected default content type", Mimetypes.MIMETYPE_OCTET_STREAM,
            basicObject.getContentType());

        // Retrieve object to ensure it was correctly created.
        basicObject = s3Service.getObject(bucket, object.getKey());
        assertEquals("Unexpected default content type", Mimetypes.MIMETYPE_OCTET_STREAM,
            basicObject.getContentType());
        assertEquals("Unexpected size for 'empty' object", 0, basicObject.getContentLength());
        basicObject.getDataInputStream().close();

        // Make sure bucket cannot be removed while it has contents.
        try {
            s3Service.deleteBucket(bucket.getName());
            fail("Cannot delete a bucket containing objects");
        } catch (S3ServiceException e) {
        }

        // Update/overwrite object to be a 'directory' object which has a specific content type and
        // no data.
        String contentType = Mimetypes.MIMETYPE_JETS3T_DIRECTORY;
        object.setContentType(contentType);
        S3Object directoryObject = s3Service.putObject(bucket, object);
        assertEquals("Unexpected default content type", contentType, directoryObject
            .getContentType());

        // Retrieve object to ensure it was correctly created.
        directoryObject = s3Service.getObject(bucket, object.getKey());
        assertEquals("Unexpected default content type", contentType, directoryObject
            .getContentType());
        assertEquals("Unexpected size for 'empty' object", 0, directoryObject.getContentLength());
        basicObject.getDataInputStream().close();

        // Update/overwrite object with real data content and some metadata.
        contentType = "text/plain";
        String objectData = "Just some rubbish text to include as data";
        String dataHash = FileComparer.computeMD5Hash(objectData.getBytes());
        HashMap metadata = new HashMap();
        metadata.put("creator", "S3ServiceTest");
        metadata.put("purpose", "For testing purposes");
        object.replaceAllMetadata(metadata);
        object.setContentType(contentType);
        object.setDataInputStream(new ByteArrayInputStream(objectData.getBytes()));
        S3Object dataObject = s3Service.putObject(bucket, object);
        assertEquals("Unexpected content type", contentType, dataObject.getContentType());
        assertEquals("Mismatching hash", dataHash, dataObject.getETag());

        // Retrieve data object to ensure it was correctly created, the server-side hash matches
        // what we expect, and we get our metadata back.
        dataObject = s3Service.getObject(bucket, object.getKey());
        assertEquals("Unexpected default content type", "text/plain", dataObject.getContentType());
        assertEquals("Unexpected size for object", objectData.length(), dataObject
            .getContentLength());
        assertEquals("Mismatching hash", dataHash, dataObject.getETag());
        assertEquals("Missing creator metadata", "S3ServiceTest", dataObject.getMetadata().get(
            "creator"));
        assertEquals("Missing purpose metadata", "For testing purposes", dataObject.getMetadata()
            .get("purpose"));
        assertNotNull("Expected data input stream to be available", dataObject.getDataInputStream());
        // Ensure we can get the data from S3.
        StringBuffer sb = new StringBuffer();
        int b = -1;
        while ((b = dataObject.getDataInputStream().read()) != -1) {
            sb.append((char) b);
        }
        dataObject.getDataInputStream().close();
        assertEquals("Mismatching data", objectData, sb.toString());

        // Retrieve only HEAD of data object (all metadata is available, but not the object content
        // data input stream)
        dataObject = s3Service.getObjectDetails(bucket, object.getKey());
        assertEquals("Unexpected default content type", "text/plain", dataObject.getContentType());
        assertEquals("Unexpected size for object", objectData.length(), dataObject.getContentLength());
        assertEquals("Mismatching hash", dataHash, dataObject.getETag());
        assertEquals("Missing creator metadata", "S3ServiceTest", dataObject.getMetadata().get(
            "creator"));
        assertEquals("Missing purpose metadata", "For testing purposes", dataObject.getMetadata()
            .get("purpose"));
        assertNull("Expected data input stream to be unavailable", dataObject.getDataInputStream());

        // Test object GET constraints.
        Calendar objectCreationTimeCal = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.US);
        objectCreationTimeCal.setTime(object.getLastModifiedDate());
        
//        objectCreationTimeCal.add(Calendar.SECOND, 1);
//        Calendar afterObjectCreation = (Calendar) objectCreationTimeCal.clone();
        objectCreationTimeCal.add(Calendar.DAY_OF_YEAR, -1);
        Calendar yesterday = (Calendar) objectCreationTimeCal.clone();
        objectCreationTimeCal.add(Calendar.DAY_OF_YEAR, +2);
        Calendar tomorrow = (Calendar) objectCreationTimeCal.clone();

        // Precondition: Modified since yesterday
        s3Service.getObjectDetails(bucket, object.getKey(), yesterday, null, null, null);
        // Precondition: Mot modified since after creation date.
        // TODO : This test fails for the REST service, why?
//        try {
//            s3Service.getObjectDetails(bucket, object.getKey(), afterObjectCreation, null, null, null);
//            fail("Cannot have been modified since object was created");
//        } catch (S3ServiceException e) { }
        // Precondition: Not modified since yesterday
        try {
            s3Service.getObjectDetails(bucket, object.getKey(), null, yesterday, null, null);
            fail("Cannot be unmodified since yesterday");
        } catch (S3ServiceException e) { }
        // Precondition: Not modified since tomorrow
        s3Service.getObjectDetails(bucket, object.getKey(), null, tomorrow, null, null);
        // Precondition: matches correct hash
        s3Service.getObjectDetails(bucket, object.getKey(), null, null, new String[] {dataHash}, null);
        // Precondition: doesn't match incorrect hash
        try {
            s3Service.getObjectDetails(bucket, object.getKey(), null, null, 
                new String[] {"__" + dataHash.substring(2)}, null);
            fail("Hash values should not match");
        } catch (S3ServiceException e) {
        }
        // Precondition: doesn't match correct hash
        try {
            s3Service.getObjectDetails(bucket, object.getKey(), null, null, null, new String[] {dataHash});
            fail("Hash values should mis-match");
        } catch (S3ServiceException e) {
        }
        // Precondition: doesn't match incorrect hash
        s3Service.getObjectDetails(bucket, object.getKey(), null, null, null, 
            new String[] {"__" + dataHash.substring(2)});

        // Retrieve only a limited byte-range of the data, with a start and end.
        Long byteRangeStart = new Long(3);
        Long byteRangeEnd = new Long(12);
        dataObject = s3Service.getObject(bucket, object.getKey(), null, null, null, null, byteRangeStart, byteRangeEnd);
        String dataReceived = readStringFromInputStream(dataObject.getDataInputStream());
        String dataExpected = objectData.substring(byteRangeStart.intValue(), byteRangeEnd.intValue() + 1);
        assertEquals("Mismatching data from range precondition", dataExpected, dataReceived);

        // Retrieve only a limited byte-range of the data, with a start range only.
        byteRangeStart = new Long(7);
        byteRangeEnd = null;
        dataObject = s3Service.getObject(bucket, object.getKey(), null, null, null, null, byteRangeStart, byteRangeEnd);
        dataReceived = readStringFromInputStream(dataObject.getDataInputStream());
        dataExpected = objectData.substring(byteRangeStart.intValue());
        assertEquals("Mismatching data from range precondition", dataExpected, dataReceived);

        // Retrieve only a limited byte-range of the data, with an end range only.
        byteRangeStart = null;
        byteRangeEnd = new Long(13);
        dataObject = s3Service.getObject(bucket, object.getKey(), null, null, null, null, byteRangeStart, byteRangeEnd);
        dataReceived = readStringFromInputStream(dataObject.getDataInputStream());
        dataExpected = objectData.substring(objectData.length() - byteRangeEnd.intValue());
        assertEquals("Mismatching data from range precondition", dataExpected, dataReceived);

        // Clean-up.
        s3Service.deleteObject(bucket, object.getKey());
        s3Service.deleteBucket(bucket.getName());
    }
    
    public void testACLManagement() throws Exception {
        String s3Url = "http://s3.amazonaws.com";
        
        // Access public "third-party" bucket
        S3Service anonymousS3Service = getS3Service(null);
        anonymousS3Service.isBucketAvailable("jetS3T");

        S3Service s3Service = getS3Service(awsCredentials);

        String bucketName = awsCredentials.getAccessKey() + ".S3ServiceTest";
        S3Bucket bucket = s3Service.createBucket(bucketName);
        S3Object object = new S3Object();

        // Create private object (default permissions).
        String privateKey = "PrivateObject";
        object.setKey(privateKey);
        s3Service.putObject(bucket, object);
        URL url = new URL(s3Url + "/" + bucketName + "/" + privateKey);
        assertEquals("Expected denied access (403) error", 403, ((HttpURLConnection) url
            .openConnection()).getResponseCode());
        
        // Get ACL details for private object so we can determine the bucket owner.
        AccessControlList bucketACL = s3Service.getAcl(bucket);
        S3Owner bucketOwner = bucketACL.getOwner();

        // Create a public object.
        String publicKey = "PublicObject";
        object.setKey(publicKey);
        AccessControlList acl = new AccessControlList();
        acl.setOwner(bucketOwner);
        acl.grantPermission(GroupGrantee.ALL_USERS, Permission.PERMISSION_READ);
        object.setAcl(acl);
        s3Service.putObject(bucket, object);
        url = new URL(s3Url + "/" + bucketName + "/" + publicKey);      
        assertEquals("Expected access (200)", 
                200, ((HttpURLConnection)url.openConnection()).getResponseCode());

        // Update ACL to make private object public.
        AccessControlList privateToPublicACL = s3Service.getAcl(bucket, privateKey);
        privateToPublicACL.grantPermission(GroupGrantee.ALL_USERS, Permission.PERMISSION_READ);
        object.setKey(privateKey);
        object.setAcl(privateToPublicACL);
        s3Service.putAcl(bucket, object);
        url = new URL(s3Url + "/" + bucketName + "/" + privateKey);
        assertEquals("Expected access (200)", 200, ((HttpURLConnection) url.openConnection())
            .getResponseCode());

        // Create a non-standard uncanned public object.
        String publicKey2 = "PublicObject2";
        object.setKey(publicKey2);
        object.setAcl(privateToPublicACL); // This ACL has ALL_USERS READ permission set above.
        s3Service.putObject(bucket, object);
        url = new URL(s3Url + "/" + bucketName + "/" + publicKey2);
        assertEquals("Expected access (200)", 200, ((HttpURLConnection) url.openConnection())
            .getResponseCode());

        // Update ACL to make public object private.
        AccessControlList publicToPrivateACL = s3Service.getAcl(bucket, publicKey);
        publicToPrivateACL.revokeAllPermissions(GroupGrantee.ALL_USERS);
        object.setKey(publicKey);
        object.setAcl(publicToPrivateACL);
        s3Service.putAcl(bucket, object);
        url = new URL(s3Url + "/" + bucketName + "/" + publicKey);
        assertEquals("Expected denied access (403) error", 403, ((HttpURLConnection) url
            .openConnection()).getResponseCode());

        // Generate URL granting anonymous user access.
        int secondsUntilExpiry = 3;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, secondsUntilExpiry);
        String urlString = S3Service.createSignedUrl(bucket.getName(), privateKey, awsCredentials,
            cal.getTimeInMillis() / 1000, false);
        url = new URL(urlString);
        assertEquals("Expected access (200)", 200, ((HttpURLConnection) url.openConnection())
            .getResponseCode());
        // Ensure anonymous user access URL expires.
        Thread.sleep((secondsUntilExpiry + 1) * 1000);
        assertEquals("Expected denied access (403) error", 403, ((HttpURLConnection) url
            .openConnection()).getResponseCode());

        // Clean-up.
        s3Service.deleteObject(bucket, privateKey);
        s3Service.deleteObject(bucket, publicKey);
        s3Service.deleteObject(bucket, publicKey2);
        s3Service.deleteBucket(bucket.getName());
    }

    private String readStringFromInputStream(InputStream is) throws IOException {
        StringBuffer sb = new StringBuffer();
        int b = -1;
        while ((b = is.read()) != -1) {
            sb.append((char)b);
        }
        return sb.toString();
    }

}
