package com.amadeus.mdi.repository.entitymanager;

/**
 *
 */

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hibernate.query.Query;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * This Class holds all the services related to call to DB by Entity Manager way
 *
 *Testing by Tusar
 *
 */
@Service
public class EntityManagerServiceImpl {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * This method executes the Native DB query using Hibernate Entity Manager
     *
     * @param query
     * @param params
     * @param listValuesMap
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<Map<String, Object>> executeQuery(String query, Map<String, Object> params, Map<String, List<?>> listValuesMap) {
        Query<Map<String, Object>> nativeQuery = ((org.hibernate.query.Query) entityManager.createNativeQuery(query));
        if (!CollectionUtils.isEmpty(listValuesMap)) {
            for (Map.Entry<String, List<?>> entry : listValuesMap.entrySet()) {
                nativeQuery.setParameterList(entry.getKey(), entry.getValue());
            }
        }
        if (!CollectionUtils.isEmpty(params)) {
            for (Entry<String, Object> entry : params.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();
                if (paramValue instanceof String) {
                    nativeQuery.setParameter(paramName, paramValue.toString());
                } else if (paramValue instanceof Integer) {
                    nativeQuery.setParameter(paramName, Integer.parseInt(paramValue.toString()));
                } else if (paramValue instanceof Long) {
                    nativeQuery.setParameter(paramName, Long.parseLong(paramValue.toString()));
                }
            }
        }

        return nativeQuery.getResultList();
    }

    /**
     * This method executes the Native DB query for Export data
     *
     * @param query
     * @param requestId
     * @return
     */
    @SuppressWarnings({ "rawtypes", "deprecation", "unchecked" })
    public List<Map<String, Object>> executeQueryAndFetchAsMap(String query, String requestId) {
        Query hibernateQuery = ((org.hibernate.query.Query) entityManager.createNativeQuery(query))
                .setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE).setParameter("requestId", requestId);

        return hibernateQuery.getResultList();
    }

}
