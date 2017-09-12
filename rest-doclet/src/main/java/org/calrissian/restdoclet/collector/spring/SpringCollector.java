/*******************************************************************************
 * Copyright (C) 2014 The Calrissian Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.calrissian.restdoclet.collector.spring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.Tag;
import org.calrissian.restdoclet.collector.AbstractCollector;
import org.calrissian.restdoclet.collector.EndpointMapping;
import org.calrissian.restdoclet.model.PathVar;
import org.calrissian.restdoclet.model.QueryParam;
import org.calrissian.restdoclet.model.RequestBody;

import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static org.calrissian.restdoclet.util.AnnotationUtils.getAnnotationName;
import static org.calrissian.restdoclet.util.AnnotationUtils.getElementValue;
import static org.calrissian.restdoclet.util.CommonUtils.firstNonEmpty;
import static org.calrissian.restdoclet.util.CommonUtils.isEmpty;
import static org.calrissian.restdoclet.util.TagUtils.PATHVAR_TAG;
import static org.calrissian.restdoclet.util.TagUtils.QUERYPARAM_TAG;
import static org.calrissian.restdoclet.util.TagUtils.REQUESTBODY_TAG;
import static org.calrissian.restdoclet.util.TagUtils.findParamComment;
import static org.calrissian.restdoclet.util.TagUtils.findParamText;

public class SpringCollector extends AbstractCollector {

    protected static final String CONTROLLER_ANNOTATION = "org.springframework.stereotype.Controller";
    protected static final String REST_CONTROLLER_ANNOTATION = "org.springframework.web.bind.annotation.RestController";
    protected static final String MAPPING_ANNOTATION = "org.springframework.web.bind.annotation.RequestMapping";
    protected static final String RESPONSE_BODY_ANNOTATION = "org.springframework.web.bind.annotation.ResponseBody";

    protected static final String PATHVAR_ANNOTATION = "org.springframework.web.bind.annotation.PathVariable";
    protected static final String PARAM_ANNOTATION = "org.springframework.web.bind.annotation.RequestParam";
    protected static final String REQUESTBODY_ANNOTATION = "org.springframework.web.bind.annotation.RequestBody";

    @Override
    protected boolean shouldIgnoreClass(ClassDoc classDoc) {
        //If found a controller annotation then don't ignore this class.
        for (AnnotationDesc classAnnotation : classDoc.annotations()) {
            String annotationName = getAnnotationName(classAnnotation);
            if (CONTROLLER_ANNOTATION.equals(annotationName) || REST_CONTROLLER_ANNOTATION.equals(annotationName)) {
                return false;
            }
        }

        //If not found then ignore this class.
        return true;
    }

    @Override
    protected boolean shouldIgnoreMethod(MethodDoc methodDoc) {
        //If found a mapping annotation then don't ignore this class.
        boolean hasMappingAnnotation = false;
        boolean hasResponseBodyAnnotation = false;
        for (AnnotationDesc classAnnotation : methodDoc.annotations()) {
            String annotationName = getAnnotationName(classAnnotation);

            if (MAPPING_ANNOTATION.equals(annotationName)) {
                hasMappingAnnotation = true;
            } else if (RESPONSE_BODY_ANNOTATION.equals(annotationName)) {
                hasResponseBodyAnnotation = true;
            }
        }

        for (AnnotationDesc annotationDesc : methodDoc.containingClass().annotations()) {
            String annotationName = getAnnotationName(annotationDesc);

            if (RESPONSE_BODY_ANNOTATION.equals(annotationName)) {
                hasResponseBodyAnnotation = true;
            }
        }

        return !hasMappingAnnotation || !hasResponseBodyAnnotation;
    }

    @Override
    protected EndpointMapping getEndpointMapping(ProgramElementDoc doc) {
        //Look for a request mapping annotation
        for (AnnotationDesc annotation : doc.annotations()) {
            //If found then extract the value (paths) and the methods.
            if (MAPPING_ANNOTATION.equals(getAnnotationName(annotation))) {

                //Get http methods from annotation
                Collection<String> httpMethods = new LinkedHashSet<String>();
                for (String value : getElementValue(annotation, "method")) {
                    httpMethods.add(value.substring(value.lastIndexOf(".") + 1));
                }

                return new EndpointMapping(
                    new LinkedHashSet<String>(getElementValue(annotation, "value")),
                    httpMethods,
                    new LinkedHashSet<String>(getElementValue(annotation, "consumes")),
                    new LinkedHashSet<String>(getElementValue(annotation, "produces"))
                );
            }
        }

        //Simply return an empty grouping if no request mapping was found.
        return new EndpointMapping(
            Collections.<String>emptySet(),
            Collections.<String>emptySet(),
            Collections.<String>emptySet(),
            Collections.<String>emptySet()
        );
    }

    @Override
    protected Collection<PathVar> generatePathVars(MethodDoc methodDoc) {
        Collection<PathVar> retVal = new ArrayList<PathVar>();

        Tag[] tags = methodDoc.tags(PATHVAR_TAG);
        ParamTag[] paramTags = methodDoc.paramTags();

        for (Parameter parameter : methodDoc.parameters()) {
            for (AnnotationDesc annotation : parameter.annotations()) {
                if (getAnnotationName(annotation).equals(PATHVAR_ANNOTATION)) {
                    String name = parameter.name();
                    Collection<String> values = getElementValue(annotation, "value");
                    if (!values.isEmpty()) { name = values.iterator().next(); }

                    //first check for special tag, then check regular param tag, finally default to empty string
                    String text = findParamText(tags, name);
                    if (text == null) { text = findParamText(paramTags, parameter.name()); }
                    if (text == null) { text = ""; }

                    retVal.add(new PathVar(name, text, parameter.type()));
                }
            }
        }

        return retVal;
    }

    @Override
    protected Collection<QueryParam> generateQueryParams(MethodDoc methodDoc) {
        Collection<QueryParam> retVal = new ArrayList<QueryParam>();

        Tag[] tags = methodDoc.tags(QUERYPARAM_TAG);
        ParamTag[] paramTags = methodDoc.paramTags();

        for (Parameter parameter : methodDoc.parameters()) {
            if (parameter.annotations() == null || parameter.annotations().length == 0) {
                String name = parameter.name();

                retVal.add(new QueryParam(name, true, findParamComment(paramTags, name), parameter.type()));
            } else {
                for (AnnotationDesc annotation : parameter.annotations()) {
                    if (getAnnotationName(annotation).equals(PARAM_ANNOTATION)) {
                        String name = parameter.name();
                        List<String> values = getElementValue(annotation, "value");
                        if (!values.isEmpty()) { name = values.get(0); }

                        List<String> requiredVals = getElementValue(annotation, "required");

                        //With spring query params are required by default
                        boolean required = TRUE;
                        if (!requiredVals.isEmpty()) { required = Boolean.parseBoolean(requiredVals.get(0)); }

                        //first check for special tag, then check regular param tag, finally default to empty string
                        String text = findParamText(tags, name);
                        if (text == null) { text = findParamText(paramTags, name); }
                        if (text == null) { text = ""; }

                        retVal.add(new QueryParam(name, required, text, parameter.type()));
                    }
                }
            }
        }
        return retVal;
    }

    @Override
    protected RequestBody generateRequestBody(MethodDoc methodDoc) {

        Tag[] tags = methodDoc.tags(REQUESTBODY_TAG);
        ParamTag[] paramTags = methodDoc.paramTags();

        for (Parameter parameter : methodDoc.parameters()) {
            for (AnnotationDesc annotation : parameter.annotations()) {
                if (getAnnotationName(annotation).equals(REQUESTBODY_ANNOTATION)) {

                    //first check for special tag, then check regular param tag, finally default to empty string
                    String text = (isEmpty(tags) ? null : tags[0].text());
                    if (text == null) { text = findParamText(paramTags, parameter.name()); }
                    if (text == null) { text = ""; }

                    return new RequestBody(parameter.name(), text, parameter.type());
                }
            }
        }
        return null;
    }

    @Override
    protected Collection<String> resolveHttpMethods(EndpointMapping classMapping, EndpointMapping methodMapping) {
        //If there are no http methods defined simply use GET
        return firstNonEmpty(super.resolveHttpMethods(classMapping, methodMapping), asList("GET"));
    }
}
