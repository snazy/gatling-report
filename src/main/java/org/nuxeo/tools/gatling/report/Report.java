/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Benoit Delbosc
 */
package org.nuxeo.tools.gatling.report;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.nuxeo.tools.gatling.report.CombinedContext.CombinedPart;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

public class Report implements AutoCloseable {
    protected static final String YAML = "yaml/";

    protected static final String HTML = "html/";

    protected static final String DEFAULT_FILENAME = "index.html";

    protected static final String COMBINED_HEAD_TEMPLATE = "combined-head.mustache";

    protected static final String COMBINED_SIMULATION_TEMPLATE = "combined-simulation.mustache";

    protected static final String COMBINED_TREND_TEMPLATE = "combined-trend.mustache";

    protected static final String COMBINED_DIFF_TEMPLATE = "combined-diff.mustache";

    protected static final String SIMULATION_TEMPLATE = "simulation.mustache";

    protected static final String TREND_TEMPLATE = "trend.mustache";

    protected static final String DIFF_TEMPLATE = "diff.mustache";

    protected static final String DEFAULT_SCRIPT = "plotly-latest.min.js";

    protected static final String DEFAULT_CDN_SCRIPT = "https://cdn.plot.ly/plotly-latest.min.js";

    protected final List<SimulationContext> stats;

    protected File outputDirectory;

    protected Writer writer;

    protected final List<String> scripts = new ArrayList<>();

    protected boolean includeJs = false;

    protected String template;

    protected String graphiteUrl, user, password;

    protected Graphite graphite;

    protected ZoneId zoneId;

    protected boolean yaml = false;

    protected boolean combined = false;

    protected List<String> map;

    protected String filename = DEFAULT_FILENAME;

    public Report(List<SimulationContext> stats) {
        this.stats = stats;
    }

    public Report setOutputDirectory(File output) {
        this.outputDirectory = output;
        return this;
    }

    public Report addScript(String script) {
        scripts.add(script);
        return this;
    }

    public Report includeJs(boolean value) {
        includeJs = value;
        return this;
    }

    public Report combined(boolean combined) {
        this.combined = combined;
        return this;
    }

    public Report setTemplate(String template) {
        this.template = template;
        return this;
    }

    public String create() throws IOException {
        int nbSimulation = stats.size();
        if (graphiteUrl != null) {
            stats.forEach(stats -> stats.simStat.graphite = new Graphite(graphiteUrl, user, password, stats,
                    outputDirectory, zoneId));
        }
        if (combined) {
            createCombinedReport();
        } else {
            switch (nbSimulation) {
            case 1:
                createSimulationReport();
                break;
            case 2:
                createDiffReport();
                break;
            default:
                createTrendReport();
            }
        }
        return getReportPath().getAbsolutePath();
    }

    public void createSimulationReport() throws IOException {
        Mustache mustache = getMustache();
        mustache.execute(getWriter(), stats.get(0).setScripts(getScripts()));
    }

    protected Mustache getMustache() throws FileNotFoundException {
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache;
        if (template == null) {
            mustache = mf.compile(getDefaultTemplate());
        } else {
            mustache = mf.compile(new FileReader(template), template);
        }
        return mustache;
    }

    public void createCombinedReport() throws IOException {
        MustacheFactory mf = new DefaultMustacheFactory();

        CombinedContext context = new CombinedContext(stats, getScripts());

        Mustache tplHead = mf.compile(getTemplatePath(COMBINED_HEAD_TEMPLATE));
        Mustache tplSimulation = mf.compile(getTemplatePath(COMBINED_SIMULATION_TEMPLATE));
        Mustache tplDiff = mf.compile(getTemplatePath(COMBINED_DIFF_TEMPLATE));
        Mustache tplTrend = mf.compile(getTemplatePath(COMBINED_TREND_TEMPLATE));

        tplHead.execute(getWriter(), context);
        for (CombinedPart part : context.getParts()) {
            Mustache tpl;
            switch (part.getType()) {
                case SIMULATION:
                    tpl = tplSimulation;
                    break;
                case DIFF:
                    tpl = tplDiff;
                    break;
                case TREND:
                    tpl = tplTrend;
                    break;
                default:
                    throw new IllegalStateException();
            }
            tpl.execute(getWriter(), part.getContext());
        }
    }

    public void createTrendReport() throws IOException {
        Mustache mustache = getMustache();
        if (map != null && map.size() == stats.size()) {
            HashMap<String, Object> scopes = new HashMap<>();
            scopes.put("trend", new TrendContext(stats).setScripts(getScripts()));
            int i = 0;
            for (String name : map) {
                scopes.put(name, stats.get(i++));
            }
            mustache.execute(getWriter(), scopes);
        } else {
            mustache.execute(getWriter(), new TrendContext(stats).setScripts(getScripts()));
        }
    }

    public void createDiffReport() throws IOException {
        Mustache mustache = getMustache();
        if (map != null && map.size() == stats.size()) {
            HashMap<String, Object> scopes = new HashMap<>();
            scopes.put("diff", new DiffContext(stats).setScripts(getScripts()));
            int i = 0;
            for (String name : map) {
                scopes.put(name, stats.get(i++));
            }
            mustache.execute(getWriter(), scopes);
        } else {
            mustache.execute(getWriter(), new DiffContext(stats).setScripts(getScripts()));
        }

    }

    public Writer getWriter() throws IOException {
        if (writer == null) {
            File index = getReportPath();
            writer = new FileWriter(index);
        }
        return writer;
    }

    public Report setWriter(Writer writer) {
        this.writer = writer;
        return this;
    }

    public File getReportPath() {
        return new File(outputDirectory, filename);
    }

    public List<String> getScripts() {
        if (scripts.isEmpty()) {
            scripts.add(getOrCreateDefaultScript());
        }
        return scripts;
    }

    public String getOrCreateDefaultScript() {
        if (outputDirectory == null || !includeJs) {
            return DEFAULT_CDN_SCRIPT;
        }
        URL src = getClass().getResource(DEFAULT_SCRIPT);
        try {
            FileUtils.copyURLToFile(src, new File(outputDirectory, DEFAULT_SCRIPT));
        } catch (IOException e) {
            throw new IllegalArgumentException("Can not copy script: " + src, e);
        }
        return DEFAULT_SCRIPT;
    }

    public String getTemplatePath(String tpl) {
        String prefix = yaml ? YAML : HTML;
        return prefix + tpl;
    }

    public String getDefaultTemplate() {
        int nbSimulation = stats.size();
        String prefix = yaml ? YAML : HTML;
        switch (nbSimulation) {
        case 1:
            return prefix + SIMULATION_TEMPLATE;
        case 2:
            return prefix + DIFF_TEMPLATE;
        default:
            return prefix + TREND_TEMPLATE;
        }
    }

    public Report includeGraphite(String graphiteUrl, String user, String password, ZoneId zoneId) {
        this.graphiteUrl = graphiteUrl;
        this.user = user;
        this.password = password;
        this.zoneId = zoneId;
        return this;
    }

    public Report yamlReport(boolean yaml) {
        this.yaml = yaml;
        return this;
    }

    public Report withMap(List<String> map) {
        this.map = map;
        return this;
    }

    public Report setFilename(String filename) {
        if (filename != null) {
            this.filename = filename;
        }
        return this;
    }

    @Override
    public void close() throws Exception {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
