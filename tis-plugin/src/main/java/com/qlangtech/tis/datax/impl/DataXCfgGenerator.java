/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 * This program is free software: you can use, redistribute, and/or modify
 * it under the terms of the GNU Affero General Public License, version 3
 * or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.qlangtech.tis.datax.impl;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.qlangtech.tis.datax.*;
import com.qlangtech.tis.manage.common.TisUTF8;
import com.qlangtech.tis.trigger.util.JsonUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author: baisui 百岁
 * @create: 2021-04-20 18:06
 **/
public class DataXCfgGenerator {

    private transient static final VelocityEngine velocityEngine;

    static {
        try {
            velocityEngine = new VelocityEngine();
            Properties prop = new Properties();
            prop.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogChute");
            velocityEngine.init(prop);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//  public final Map<String, Object> getSetting() {
//    Map<String, Object> setting = Maps.newHashMap();
//    Map<String, Object> speed = Maps.newHashMap();
//    speed.put("channel", dataxProcessor.getChannel());
//    Map<String, Object> errorLimit = Maps.newHashMap();
//    errorLimit.put("record", dataxProcessor.getErrorLimitCount());
//    errorLimit.put("percentage", dataxProcessor.getErrorLimitPercentage());
//    setting.put("speed", speed);
//    setting.put("errorLimit", errorLimit);
//    return setting;
//  }

    private final IDataxProcessor dataxProcessor;
    private final IDataxGlobalCfg globalCfg;

    public DataXCfgGenerator(IDataxProcessor dataxProcessor) {
        Objects.requireNonNull(dataxProcessor, "dataXprocessor can not be null");
        IDataxGlobalCfg dataXGlobalCfg = dataxProcessor.getDataXGlobalCfg();
        Objects.requireNonNull(dataXGlobalCfg, "globalCfg can not be null");
        this.dataxProcessor = dataxProcessor;
        this.globalCfg = dataXGlobalCfg;
    }

    public String getTemplateContent() {
        final String tpl = globalCfg.getTemplate();
        String template = StringUtils.replace(tpl, "<!--reader-->", dataxProcessor.getReader().getTemplate());
        template = StringUtils.replace(template, "<!--writer-->", dataxProcessor.getWriter().getTemplate());
        return template;
    }


    /**
     * 取得之前已经存在的文件
     *
     * @param parentDir
     * @return
     */
    public GenerateCfgs getExistCfg(File parentDir) throws Exception {
        GenerateCfgs generateCfgs = new GenerateCfgs();
        IDataxReader reader = dataxProcessor.getReader();
        Iterator<IDataxReaderContext> subTasks = reader.getSubTasks();
        IDataxReaderContext readerContext = null;
        File configFile = null;
        List<String> subTaskName = Lists.newArrayList();
        while (subTasks.hasNext()) {
            readerContext = subTasks.next();
            configFile = new File(parentDir, readerContext.getTaskName() + ".json");
            subTaskName.add(configFile.getName());
        }

        generateCfgs.genTime = Long.parseLong(FileUtils.readFileToString(new File(parentDir, FILE_GEN), TisUTF8.get()));
        generateCfgs.dataxFiles = subTaskName;
        return generateCfgs;
    }


    private static final String FILE_GEN = "gen";

    public GenerateCfgs startGenerateCfg(File parentDir) throws Exception {
        GenerateCfgs cfgs = new GenerateCfgs();
        boolean unStructed2RDBMS = dataxProcessor.isUnStructed2RDBMS();

        IDataxReader reader = dataxProcessor.getReader();
        Map<String, IDataxProcessor.TableAlias> tabAlias = dataxProcessor.getTabAlias();

        AtomicReference<Map<String, ISelectedTab>> selectedTabsRef = new AtomicReference<>();
        java.util.concurrent.Callable<Map<String, ISelectedTab>> selectedTabsCall = () -> {
            if (selectedTabsRef.get() == null) {
                Map<String, ISelectedTab> selectedTabs = reader.getSelectedTabs().stream().collect(Collectors.toMap((t) -> t.getName(), (t) -> t));
                selectedTabsRef.set(selectedTabs);
            }
            return selectedTabsRef.get();
        };

        Iterator<IDataxReaderContext> subTasks = reader.getSubTasks();
        IDataxReaderContext readerContext = null;
        File configFile = null;
        List<String> subTaskName = Lists.newArrayList();
        IDataxProcessor.TableMap tableMapper = null;
        while (subTasks.hasNext()) {
            readerContext = subTasks.next();
            if (unStructed2RDBMS) {
                // 是在DataxAction的doSaveWriterColsMeta() 方法中持久化保存的
                for (IDataxProcessor.TableAlias tab : tabAlias.values()) {
                    tableMapper = (IDataxProcessor.TableMap) tab;
                    break;
                }
                Objects.requireNonNull(tableMapper, "tableMap can not be null");
            } else {
                tableMapper = createTableMap(tabAlias, selectedTabsCall.call(), readerContext);
            }
            configFile = new File(parentDir, readerContext.getTaskName() + ".json");
            FileUtils.write(configFile, generateDataxConfig(readerContext, Optional.of(tableMapper)), TisUTF8.get(), false);
            subTaskName.add(configFile.getName());
        }
        long current = System.currentTimeMillis();
        FileUtils.write(new File(parentDir, FILE_GEN), String.valueOf(current), TisUTF8.get(), false);
        cfgs.dataxFiles = subTaskName;
        cfgs.genTime = current;
        return cfgs;
    }

    private static class GenerateCfgs {
        private List<String> dataxFiles;
        private long genTime;

        public List<String> getDataxFiles() {
            return dataxFiles;
        }

        public long getGenTime() {
            return genTime;
        }
    }

    private IDataxProcessor.TableMap createTableMap(Map<String, IDataxProcessor.TableAlias> tabAlias
            , Map<String, ISelectedTab> selectedTabs, IDataxReaderContext readerContext) {
        IDataxProcessor.TableAlias tableAlias = tabAlias.get(readerContext.getSourceEntityName());
        if (tableAlias == null) {
            throw new IllegalStateException("sourceTable:" + readerContext.getSourceEntityName() + " can not find relevant 'tableAlias'");
        }
        ISelectedTab selectedTab = selectedTabs.get(readerContext.getSourceEntityName());
        IDataxProcessor.TableMap
                tableMap = new IDataxProcessor.TableMap();
        tableMap.setFrom(tableAlias.getFrom());
        tableMap.setTo(tableAlias.getTo());
        tableMap.setSourceCols(selectedTab.getCols());
        return tableMap;
    }

    /**
     * @param readerContext
     * @param tableMap
     * @return 生成的配置文件内容
     * @throws IOException
     */
    public String generateDataxConfig(IDataxReaderContext readerContext, Optional<IDataxProcessor.TableMap> tableMap) throws IOException {

        StringWriter writerContent = null;
        final String tpl = getTemplateContent();
        if (StringUtils.isEmpty(tpl)) {
            throw new IllegalStateException("velocity template content can not be null");
        }
        try {
            IDataxWriter writer = dataxProcessor.getWriter();

            VelocityContext mergeData = createContext(readerContext, writer.getSubTask(tableMap));
            writerContent = new StringWriter();
            velocityEngine.evaluate(mergeData, writerContent, "tablex-writer.vm", tpl);
        } catch (Exception e) {
            throw new RuntimeException(tpl + "\n", e);
        }
        String content = writerContent.toString();
        try {
            return JsonUtil.toString(JSON.parseObject(content));
        } catch (Exception e) {
            throw new RuntimeException(content, e);
        }
    }

    private VelocityContext createContext(IDataxContext reader, IDataxContext writer) {
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("reader", reader);
        velocityContext.put("writer", writer);
        velocityContext.put("cfg", this.globalCfg);
        return velocityContext;
    }
}