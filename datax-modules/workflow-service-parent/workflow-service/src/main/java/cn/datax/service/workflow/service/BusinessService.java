package cn.datax.service.workflow.service;

import cn.datax.service.workflow.api.entity.BusinessEntity;
import cn.datax.service.workflow.api.dto.BusinessDto;
import cn.datax.common.base.BaseService;

import java.util.List;

/**
 * <p>
 * 业务流程配置表 服务类
 * </p>
 *
 * @author yuwei
 * @since 2020-09-22
 */
public interface BusinessService extends BaseService<BusinessEntity> {

    BusinessEntity saveBusiness(BusinessDto business);

    BusinessEntity updateBusiness(BusinessDto business);

    BusinessEntity getBusinessById(String id);

    void deleteBusinessById(String id);

    void deleteBusinessBatch(List<String> ids);

    void refreshBusiness();
}
