package cn.datax.service.workflow.service.impl;

import cn.datax.common.utils.SecurityUtil;
import cn.datax.service.workflow.api.dto.TaskRequest;
import cn.datax.service.workflow.api.enums.ActionEnum;
import cn.datax.service.workflow.api.enums.VariablesEnum;
import cn.datax.service.workflow.api.query.FlowTaskQuery;
import cn.datax.service.workflow.api.vo.FlowHistTaskVo;
import cn.datax.service.workflow.api.vo.FlowTaskVo;
import cn.datax.service.workflow.service.FlowTaskService;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class FlowTaskServiceImpl implements FlowTaskService {

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Override
    public Page<FlowTaskVo> pageTodo(FlowTaskQuery flowTaskQuery) {
        TaskQuery taskQuery = taskService.createTaskQuery();
        taskQuery.taskCandidateOrAssigned(SecurityUtil.getUserId()).taskCandidateGroupIn(SecurityUtil.getUserRoleIds());
        if(StrUtil.isNotBlank(flowTaskQuery.getBusinessKey())){
            taskQuery.processInstanceBusinessKey(flowTaskQuery.getBusinessKey());
        }
        if(StrUtil.isNotBlank(flowTaskQuery.getBusinessCode())){
            taskQuery.processVariableValueEquals(VariablesEnum.businessCode.toString(), flowTaskQuery.getBusinessCode());
        }
        if(StrUtil.isNotBlank(flowTaskQuery.getBusinessName())){
            taskQuery.processVariableValueLike(VariablesEnum.businessName.toString(), flowTaskQuery.getBusinessName());
        }
        List<Task> taskList = taskQuery.includeProcessVariables()
                .orderByTaskCreateTime().asc()
                .listPage((flowTaskQuery.getPageNum() - 1) * flowTaskQuery.getPageSize(), flowTaskQuery.getPageSize());
        List<FlowTaskVo> list = new ArrayList<>();
        taskList.stream().forEach(task -> {
            FlowTaskVo flowTaskVo = new FlowTaskVo();
            BeanUtil.copyProperties(task, flowTaskVo, "variables");
            //??????????????????
            flowTaskVo.setVariables(task.getProcessVariables());
            //??????????????????
            flowTaskVo.setIsDelegation(DelegationState.PENDING.equals(task.getDelegationState()));
            list.add(flowTaskVo);
        });
        long count = taskQuery.count();
        Page<FlowTaskVo> page = new Page<>(flowTaskQuery.getPageNum(), flowTaskQuery.getPageSize());
        page.setRecords(list);
        page.setTotal(count);
        return page;
    }

    @Override
    public Page<FlowHistTaskVo> pageDone(FlowTaskQuery flowTaskQuery) {
        HistoricTaskInstanceQuery historicTaskInstanceQuery = historyService.createHistoricTaskInstanceQuery();
        historicTaskInstanceQuery.taskAssignee(SecurityUtil.getUserId());
        if (StrUtil.isNotBlank(flowTaskQuery.getBusinessKey())) {
            historicTaskInstanceQuery.processInstanceBusinessKey(flowTaskQuery.getBusinessKey());
        }
        if (StrUtil.isNotBlank(flowTaskQuery.getBusinessCode())) {
            historicTaskInstanceQuery.processVariableValueEquals(VariablesEnum.businessCode.toString(), flowTaskQuery.getBusinessCode());
        }
        if (StrUtil.isNotBlank(flowTaskQuery.getBusinessName())) {
            historicTaskInstanceQuery.processVariableValueLike(VariablesEnum.businessName.toString(), flowTaskQuery.getBusinessName());
        }
        List<HistoricTaskInstance> historicTaskInstanceList = historicTaskInstanceQuery.finished()
                .includeProcessVariables().orderByHistoricTaskInstanceEndTime().desc()
                .listPage((flowTaskQuery.getPageNum() - 1) * flowTaskQuery.getPageSize(), flowTaskQuery.getPageSize());
        List<FlowHistTaskVo> list = new ArrayList<>();
        historicTaskInstanceList.stream().forEach(task -> {
            FlowHistTaskVo flowHistTaskVo = new FlowHistTaskVo();
            BeanUtil.copyProperties(task, flowHistTaskVo, "variables");
            //??????????????????
            flowHistTaskVo.setVariables(task.getProcessVariables());
            list.add(flowHistTaskVo);
        });
        long count = historicTaskInstanceQuery.count();
        Page<FlowHistTaskVo> page = new Page<>(flowTaskQuery.getPageNum(), flowTaskQuery.getPageSize());
        page.setRecords(list);
        page.setTotal(count);
        return page;
    }

    @Override
    public void execute(TaskRequest request) {
        String action = request.getAction();
        String processInstanceId = request.getProcessInstanceId();
        String taskId = request.getTaskId();
        String userId = request.getUserId();
        String message = request.getMessage();
        Map<String, Object> variables = request.getVariables();
        log.info("??????????????????:{},????????????ID:{},????????????ID:{},?????????ID:{},??????:{}", action, processInstanceId, taskId, userId, variables);
        Assert.notNull(action, "???????????????????????????");
        Assert.notNull(processInstanceId, "?????????????????????ID");
        Assert.notNull(taskId, "???????????????ID");
        ActionEnum actionEnum = ActionEnum.actionOf(action);
        switch (actionEnum) {
            case COMPLETE:
                //????????????
                this.completeTask(taskId, variables, processInstanceId, message);
                break;
            case CLAIM:
                //????????????
                this.claimTask(taskId, processInstanceId);
                break;
            case UNCLAIM:
                //?????????
                this.unClaimTask(taskId, processInstanceId);
                break;
            case DELEGATE:
                //????????????
                this.delegateTask(taskId, userId, processInstanceId);
                break;
            case RESOLVE:
                //????????????
                this.resolveTask(taskId, variables, processInstanceId);
                break;
            case ASSIGNEE:
                //????????????
                this.assigneeTask(taskId, userId, processInstanceId);
                break;
            default:
                break;
        }
    }

    private void completeTask(String taskId, Map<String, Object> variables, String processInstanceId, String message) {
        log.info("????????????ID:{}", taskId);
        Boolean approved = (Boolean) Optional.ofNullable(variables).map(s -> s.get(VariablesEnum.approved.toString())).orElse(true);
        this.addComment(ActionEnum.COMPLETE, taskId, processInstanceId, StrUtil.isBlank(message) ? (approved ? "????????????" : "???????????????") : message);
        taskService.complete(taskId, variables);
    }

    private void claimTask(String taskId, String processInstanceId) {
        log.info("????????????ID:{},?????????ID:{}", taskId, SecurityUtil.getUserId());
        this.addComment(ActionEnum.CLAIM, taskId, processInstanceId, null);
        taskService.claim(taskId, SecurityUtil.getUserId());
    }

    private void unClaimTask(String taskId, String processInstanceId) {
        log.info("???????????????ID:{}", taskId);
        this.addComment(ActionEnum.UNCLAIM, taskId, processInstanceId, null);
        taskService.unclaim(taskId);
    }

    private void delegateTask(String taskId, String userId, String processInstanceId) {
        log.info("????????????ID:{},???????????????ID:{}", taskId, userId);
        this.addComment(ActionEnum.DELEGATE, taskId, processInstanceId, null);
        taskService.delegateTask(taskId, userId);
    }

    private void resolveTask(String taskId, Map<String, Object> variables, String processInstanceId) {
        log.info("????????????ID:{}", taskId);
        this.addComment(ActionEnum.RESOLVE, taskId, processInstanceId, null);
        taskService.resolveTask(taskId);
        taskService.complete(taskId, variables);
    }

    private void assigneeTask(String taskId, String userId, String processInstanceId) {
        log.info("????????????ID:{},???????????????ID:{}", taskId, userId);
        this.addComment(ActionEnum.ASSIGNEE, taskId, processInstanceId, null);
        taskService.setAssignee(taskId, userId);
    }

    private void addComment(ActionEnum actionEnum, String taskId, String processInstanceId, String message) {
        log.info("??????????????????????????????????????????:??????ID:{},????????????ID{}", taskId, processInstanceId);
        Comment comment = taskService.addComment(taskId, processInstanceId, StrUtil.isBlank(message) ? actionEnum.getTitle() : message);
        comment.setUserId(SecurityUtil.getUserId());
        taskService.saveComment(comment);
    }
}
