/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailet;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;

import com.google.common.base.Strings;

/**
 * <p>
 * Base for singleton and aggregate reports on mailets and matchers.
 * </p>
 */
public abstract class AbstractMailetdocsReport extends AbstractMavenReport {
    
    /**
     * Directory where reports will go.
     * 
     * @parameter expression="${project.reporting.outputDirectory}"
     * @required
     * @readonly
     */
    private String outputDirectory;

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @component
     * @required
     * @readonly
     */
    private Renderer siteRenderer;

    /**
     * Extracts only a given type from a list.
     */
    private static final class TypePredicate implements Predicate {
        
        private final MailetMatcherDescriptor.Type type;

        public TypePredicate(MailetMatcherDescriptor.Type typeMatcher) {
            this.type = typeMatcher;
        }

        public boolean evaluate(Object subject) {
            return ((MailetMatcherDescriptor) subject).getType() == type;
        }
    }

    protected void executeReport(Locale locale) throws MavenReportException {
        
        getLog().info("Executing Mailets/Matchers Report");

        getSink().head();
        getSink().title();
        getSink().text("Mailet and Matchers Reference");
        getSink().title_();
        getSink().head_();

        getSink().body();

        getSink().section1();
        getSink().sectionTitle1();
        getSink().text("Mailets and Matchers Reference");
        getSink().sectionTitle1_();
        getSink().section1_();
        
        writeDescriptions();

        getSink().body_();

        getSink().flush();
        getSink().close();
    }

    private void writeDescriptions() {
        
        final List<MailetMatcherDescriptor> descriptors = buildSortedDescriptors();
        
        @SuppressWarnings("unchecked")
        final List<MailetMatcherDescriptor> matchers = (List<MailetMatcherDescriptor>) CollectionUtils.select(descriptors,
                new TypePredicate(MailetMatcherDescriptor.Type.MATCHER));
        @SuppressWarnings("unchecked")
        final List<MailetMatcherDescriptor> mailets = (List<MailetMatcherDescriptor>) CollectionUtils.select(descriptors,
                new TypePredicate(MailetMatcherDescriptor.Type.MAILET));
        
        final boolean matchersExist = matchers.size() > 0;
        final boolean mailetsExist = mailets.size() > 0;
        if (matchersExist && mailetsExist) {
            getSink().table();
            getSink().tableRow();
            getSink().tableCell();
        }
        if (matchersExist) {
            outputDescriptorIndex(matchers, "Matchers");
        }
        if (matchersExist && mailetsExist) {
            getSink().tableCell_();
            getSink().tableCell();
        }
        if (mailetsExist) {
            outputDescriptorIndex(mailets, "Mailets");
        }
        if (matchersExist && mailetsExist) {
            getSink().tableCell_();
            getSink().tableRow_();
            getSink().table_();
        }

        if (matchersExist) {
            outputDescriptorList(matchers, "Matchers");
        }
        if (mailetsExist) {
            outputDescriptorList(mailets, "Mailets");
        }
    }

    private List<MailetMatcherDescriptor> buildSortedDescriptors() {
        
        final List<MailetMatcherDescriptor> descriptors = buildDescriptors(this.project);
        
        Collections.sort(descriptors, new Comparator<MailetMatcherDescriptor>() {

            public int compare(MailetMatcherDescriptor one, MailetMatcherDescriptor two) {
                return one.getName().compareTo(two.getName());
            }

        });
        logDescriptors(descriptors);
        return descriptors;
    }

    private void logDescriptors(
            final List<MailetMatcherDescriptor> descriptors) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Built descriptors: " + descriptors);
        }
    }

    protected abstract List<MailetMatcherDescriptor> buildDescriptors(MavenProject project);

    private void outputDescriptorIndex(List<MailetMatcherDescriptor> descriptors, String title) {
        
        getSink().section2();
        getSink().sectionTitle2();
        getSink().text(title);
        getSink().sectionTitle2_();

        getSink().list();
        for (MailetMatcherDescriptor descriptor : descriptors) {
            getSink().listItem();
            getSink().link("#" + descriptor.getName());
            getSink().text(descriptor.getName());
            getSink().link_();
            getSink().listItem_();
        }
        getSink().list_();

        getSink().section2_();

    }

    private void outputDescriptorList(List<MailetMatcherDescriptor> descriptors, String title) {
        
        getSink().section1();
        getSink().sectionTitle1();
        getSink().text(title);
        getSink().sectionTitle1_();

        for (MailetMatcherDescriptor descriptor : descriptors) {
            getSink().section2();

            getSink().sectionTitle2();
            getSink().anchor(descriptor.getName());
            getSink().text(descriptor.getName());
            getSink().anchor_();
            getSink().sectionTitle2_();

            if (descriptor.getInfo() != null) {
                getSink().paragraph();
                if (descriptor.getType() == MailetMatcherDescriptor.Type.MAILET) {
                    getSink().text("Mailet Info: ");
                } else if (descriptor.getType() == MailetMatcherDescriptor.Type.MATCHER) {
                    getSink().text("Matcher Info: ");
                } else {
                    getSink().text("Info: ");
                }
                getSink().bold();
                getSink().text(descriptor.getInfo());
                getSink().bold_();
                getSink().lineBreak();
                getSink().paragraph_();
            }

            getSink().paragraph();
            String classDocs = descriptor.getClassDocs();
            if (!Strings.isNullOrEmpty(classDocs)) {
                getSink().rawText(classDocs);
            }
            getSink().paragraph_();

            getSink().section2_();

        }

        getSink().section1_();
    }

    protected MavenProject getProject() {
        return project;
    }

    protected String getOutputDirectory() {
        return outputDirectory;
    }

    protected Renderer getSiteRenderer() {
        return siteRenderer;
    }

    public String getDescription(Locale arg0) {
        return "Documentation about bundled mailets";
    }

    public String getName(Locale arg0) {
        return "Mailet Reference";
    }

    public String getOutputName() {
        return "mailet-report";
    }

    /**
     * @param siteRenderer
     *                The siteRenderer to set.
     */
    public void setSiteRenderer(Renderer siteRenderer) {
        this.siteRenderer = siteRenderer;
    }

    /**
     * For testing purpose only.
     * 
     * @param project
     *                The project to set.
     */
    public void setProject(MavenProject project) {
        this.project = project;
    }

}
