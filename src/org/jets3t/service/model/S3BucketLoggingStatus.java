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
package org.jets3t.service.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.jets3t.service.Constants;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.acl.GrantAndPermission;

import com.jamesmurty.utils.XMLBuilder;

/**
 * Represents Bucket Logging Status settings used to control bucket-based Server Access Logging in S3.
 * <p>
 * For logging to be enabled for a bucket both the targetBucketName and logfilePrefix must be
 * non-null, and the named bucket must exist. When both variables are non-null, this object
 * represents an <b>enabled</b> logging status (as indicated by {@link #isLoggingEnabled()}) and
 * the XML document generated by {@link #toXml()} will enable logging for the named bucket when
 * provided to {@link org.jets3t.service.S3Service#setBucketLoggingStatus(String, S3BucketLoggingStatus, boolean)}.
 * <p>
 * If either the targetBucketName or logfilePrefix are null, this object will represent a
 * <b>disabled</b> logging status (as indicated by {@link #isLoggingEnabled()}) and
 * the XML document generated by {@link #toXml()} will disable logging for the named bucket when
 * provided to {@link org.jets3t.service.S3Service#setBucketLoggingStatus(String, S3BucketLoggingStatus, boolean)}.
 *
 * @author James Murty
 *
 */
public class S3BucketLoggingStatus {
    private String targetBucketName = null;
    private String logfilePrefix = null;
    private final List targetGrantsList = new ArrayList();

    public S3BucketLoggingStatus() {
    }

    public S3BucketLoggingStatus(String targetBucketName, String logfilePrefix) {
        this.targetBucketName = targetBucketName;
        this.logfilePrefix = logfilePrefix;
    }

    public boolean isLoggingEnabled() {
        return targetBucketName != null
            && logfilePrefix != null;
    }

    public String getLogfilePrefix() {
        return logfilePrefix;
    }

    public void setLogfilePrefix(String logfilePrefix) {
        this.logfilePrefix = logfilePrefix;
    }

    public String getTargetBucketName() {
        return targetBucketName;
    }

    public void setTargetBucketName(String targetBucketName) {
        this.targetBucketName = targetBucketName;
    }

    public GrantAndPermission[] getTargetGrants() {
        return (GrantAndPermission[]) targetGrantsList.toArray(
            new GrantAndPermission[targetGrantsList.size()]);
    }

    public void setTargetGrants(GrantAndPermission[] targetGrants) {
        targetGrantsList.clear();
        targetGrantsList.addAll(Arrays.asList(targetGrants));
    }

    public void addTargetGrant(GrantAndPermission targetGrant) {
        targetGrantsList.add(targetGrant);
    }

    @Override
    public String toString() {
        String result = "LoggingStatus enabled=" + isLoggingEnabled();
        if (isLoggingEnabled()) {
            result += ", targetBucketName=" + getTargetBucketName()
                + ", logfilePrefix=" + getLogfilePrefix();
        }
        result += ", targetGrants=[" + targetGrantsList + "]";
        return result;
    }

    /**
     *
     * @return
     * An XML representation of the object suitable for use as an input to the REST/HTTP interface.
     *
     * @throws FactoryConfigurationError
     * @throws ParserConfigurationException
     * @throws TransformerException
     */
    public String toXml() throws S3ServiceException {
        try {
            return toXMLBuilder().asString();
        } catch (Exception e) {
            throw new S3ServiceException("Failed to build XML document for ACL", e);
        }
    }

    public XMLBuilder toXMLBuilder() throws ParserConfigurationException,
        FactoryConfigurationError, TransformerException
    {
        XMLBuilder builder = XMLBuilder.create("BucketLoggingStatus")
            .attr("xmlns", Constants.XML_NAMESPACE);

        if (isLoggingEnabled()) {
            builder.elem("LoggingEnabled")
                .elem("TargetBucket").text(getTargetBucketName()).up()
                .elem("TargetPrefix").text(getLogfilePrefix()).up();
            if (targetGrantsList.size() > 0) {
                Iterator targetGrantsIter = targetGrantsList.iterator();
                XMLBuilder grantsBuilder = builder.elem("TargetGrants");
                while (targetGrantsIter.hasNext()) {
                    GrantAndPermission gap = (GrantAndPermission) targetGrantsIter.next();
                    grantsBuilder.elem("Grant")
                        .importXMLBuilder(gap.getGrantee().toXMLBuilder())
                        .elem("Permission").text(gap.getPermission().toString());
                }
            }
        }
        return builder;
    }

}
