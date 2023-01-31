/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package com.xwiki.taskmanager.internal.macro;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.RawBlock;
import org.xwiki.rendering.block.match.MetadataBlockMatcher;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import com.xwiki.taskmanager.TaskException;
import com.xwiki.taskmanager.TaskManager;
import com.xwiki.taskmanager.TaskManagerConfiguration;
import com.xwiki.taskmanager.internal.TaskBlockProcessor;
import com.xwiki.taskmanager.macro.TaskMacroParameters;
import com.xwiki.taskmanager.model.Task;

/**
 * Task macro that will enable the users to assign tasks to each other.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("task")
@Singleton
public class TaskMacro extends AbstractMacro<TaskMacroParameters>
{
    private static final String HTML_CLASS = "class";

    @Inject
    private TaskBlockProcessor taskBlockProcessor;

    @Inject
    @Named("ssx")
    private SkinExtension ssx;

    @Inject
    @Named("jsx")
    private SkinExtension jsx;

    @Inject
    private TaskManager taskManager;

    @Inject
    private TaskManagerConfiguration conf;

    /**
     * Default constructor.
     */
    public TaskMacro()
    {
        super("name", "description", new DefaultContentDescriptor("Content of the task.", false,
            Block.LIST_BLOCK_TYPE), TaskMacroParameters.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(TaskMacroParameters parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        this.ssx.use(DateMacro.SKIN_RESOURCES_DOCUMENT_REFERENCE);
        this.jsx.use(DateMacro.SKIN_RESOURCES_DOCUMENT_REFERENCE);

        List<Block> contentBlocks = new ArrayList<>();

        Task task = taskManager.getTaskByReference(parameters.getReference());

        if (content != null && !content.trim().isEmpty()) {
            try {
                List<Block> macroContent =
                    taskBlockProcessor.getTaskContentXDOM(context.getCurrentMacroBlock(), context.getSyntax())
                        .getChildren();

                contentBlocks =
                    Collections.singletonList(new MetaDataBlock(macroContent, this.getNonGeneratedContentMetaData()));
            } catch (TaskException e) {
                throw new MacroExecutionException("Failed to get the content of the task.", e);
            }
        } else if (parameters.getReference() != null) {
            contentBlocks = getContentFromTaskPage(task, parameters.getReference());
        }

        return createTaskStructure(parameters, context, contentBlocks, task);
    }

    private List<Block> createTaskStructure(TaskMacroParameters parameters, MacroTransformationContext context,
        List<Block> contentBlocks, Task task)
    {
        Block ret;
        Map<String, String> blockParameters = new HashMap<>();

        ret = context.isInline() ? new FormatBlock() : new GroupBlock();
        MetaDataBlock sourceBlock =
            context.getCurrentMacroBlock().getFirstBlock(new MetadataBlockMatcher(MetaData.SOURCE),
                Block.Axes.ANCESTOR);
        String sourceDocument = "";
        if (sourceBlock != null) {
            sourceDocument = sourceBlock.getMetaData().getMetaData("source").toString();
        }
        blockParameters.put(HTML_CLASS, "task-macro");
        blockParameters.put("data-source", sourceDocument);
        ret.setParameters(blockParameters);
        String checked = "";
        if ((parameters.getStatus() != null && parameters.getStatus().equals(Task.STATUS_DONE)) || (task != null
            && task.getStatus().equals(Task.STATUS_DONE)))
        {
            checked = "checked";
        }
        String htmlCheckbox = String.format("<input type=\"checkbox\" data-taskId=\"%s\" %s class=\"task-status\">",
            parameters.getReference(),
            checked);
        Block checkBoxBlock = new RawBlock(htmlCheckbox, Syntax.HTML_5_0);

        ret.addChild(new FormatBlock(Collections.singletonList(checkBoxBlock), Format.NONE));
        if (task != null && task.getNumber() > 0) {
            ret.addChild(taskBlockProcessor.createTaskLinkBlock(parameters.getReference(), task.getNumber()));
        }
        ret.addChild(new GroupBlock(contentBlocks, Collections.singletonMap(HTML_CLASS, "task-content")));
        return Collections.singletonList(ret);
    }

    private List<Block> getContentFromTaskPage(Task task, String reference) throws MacroExecutionException
    {
        List<Block> contentBlocks;

        if (task == null) {
            throw new MacroExecutionException(String.format("The page [%s] does not have a Task Object.", reference));
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat(conf.getStorageDateFormat());
            List<Block> macroContent = taskBlockProcessor.generateTaskContentBlocks(
                task.getAssignee().toString(),
                task.getDuedate(),
                task.getName(),
                format
            );

            contentBlocks =
                Collections.singletonList(new MetaDataBlock(macroContent, this.getNonGeneratedContentMetaData()));
        } catch (TaskException e) {
            throw new MacroExecutionException("Failed to generate the content for the task.", e);
        }
        return contentBlocks;
    }
}
