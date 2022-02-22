/**
 *
 */
package com.amadeus.mdi.service.configcatalogue;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.amadeus.mdi.beans.ProjectBean;
import com.amadeus.mdi.beans.RequestBean;
import com.amadeus.mdi.beans.configcatalogue.CategoryConfigurationBean;
import com.amadeus.mdi.beans.configcatalogue.ConfigDataBean;
import com.amadeus.mdi.beans.configcatalogue.ConfigEntryInputBean;
import com.amadeus.mdi.beans.configcatalogue.EntryBean;
import com.amadeus.mdi.beans.configcatalogue.EntryIBean;
import com.amadeus.mdi.beans.configcatalogue.EntryParamsBean;
import com.amadeus.mdi.beans.configcatalogue.RequestAcceptBean;
import com.amadeus.mdi.beans.configcatalogue.SearchEntryBean;
import com.amadeus.mdi.beans.configcatalogue.SelectedSearchedEntriesBean;
import com.amadeus.mdi.constants.Constants;
import com.amadeus.mdi.constants.OperationsEnum;
import com.amadeus.mdi.constants.ServiceNameEnum;
import com.amadeus.mdi.constants.StatusEnum;
import com.amadeus.mdi.constants.TestSystemCategoryEnum;
import com.amadeus.mdi.entity.Request;
import com.amadeus.mdi.entity.UserProfile;
import com.amadeus.mdi.entity.configcatalogue.ConfigEntryParam;
import com.amadeus.mdi.exception.InjectionException;
import com.amadeus.mdi.exception.InvalidDataException;
import com.amadeus.mdi.exception.NoEntityFoundException;
import com.amadeus.mdi.exception.OperationNotAllowedException;
import com.amadeus.mdi.exception.UserNotFoundException;
import com.amadeus.mdi.exception.WinAproachRecordException;
import com.amadeus.mdi.json.RequestDataInputBean;
import com.amadeus.mdi.json.RequestServiceDetailsBean;
import com.amadeus.mdi.repository.RequestRepository;
import com.amadeus.mdi.repository.configcatalogue.ConfigEntryDetailsRepository;
import com.amadeus.mdi.repository.configcatalogue.ConfigEntryRepository;
import com.amadeus.mdi.repository.entitymanager.EntityManagerServiceImpl;
import com.amadeus.mdi.service.request.ProjectService;
import com.amadeus.mdi.service.request.RequestService;
import com.amadeus.mdi.service.request.RequestServiceImpl;
import com.amadeus.mdi.service.testsystem.TestSystemService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

/**
 * This class holds all the services related to Request related activites
 *
 * @author sav
 *
 */
@PropertySource("classpath:searchentry.properties")
@Service
@Transactional(rollbackFor = Exception.class)
public class ConfigRequestServiceImpl extends ConfigEntryCatalogueAbstractService implements ConfigRequestService {

    @Autowired
    private RequestServiceImpl requestServiceImpl;

    @Autowired
    EntityManagerServiceImpl entityManagerServiceImpl;

    @Autowired
    private ConfigEntryParamService configEntryParamService;

    @Autowired
    private ConfigEntryRepository configEntryRepository;

    @Value("${entry.search.sql}")
    private String entrySearchSql;

    @Value("${entry.expiry.date.column}")
    private String entryExpiryDate;

    @Value("${win.approach.column}")
    private String winApproachColumn;

    @Value("${entry.create.date.column}")
    private String entryCreateDate;

    @Value("${entry.group.column}")
    private String entryGroup;

    @Value("${entry.phase.column}")
    private String entryPhase;

    @Value("${entry.column}")
    private String entryColumn;

    @Value("${entry.category.column}")
    private String entryCategoryColumn;

    @Value("${entry.execution.column}")
    private String entryExecutionColumn;

    @Value("${entry.archived.column}")
    private String entryArchivedColumn;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private TestSystemService testSystemService;

    @Autowired
    private ConfigEntryServiceImpl configEntryService;

    private static Map<String, List<EntryBean>> entryBeanMap = new HashMap<>();

    private static Map<String, EntryParamsBean> configEntryParamMap = new HashMap<>();

    @Autowired
    private RequestService requestService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ConfigEntryDetailsRepository configEntryDetailsRepository;

    @Autowired
    private ConfigAcceptanceService configAcceptanceService;

    /**
     * This method modifies request status
     *
     * @param requestAcceptBean
     * @param userProfile
     * @throws NoEntityFoundException
     */
    @Override
    public String acceptRejectRequest(RequestAcceptBean requestAcceptBean, UserProfile userProfile)
            throws NoEntityFoundException, OperationNotAllowedException {
        Request request = this.requestServiceImpl.getRequestById(requestAcceptBean.getRequestId());
        if (requestAcceptBean.isAccepted()) {
            this.requestServiceImpl.throwIfOperationNotPermit(request, userProfile, OperationsEnum.ACCEPT);
            request.setStatus(StatusEnum.PROCESSING.name());
        } else {
            this.requestServiceImpl.throwIfOperationNotPermit(request, userProfile, OperationsEnum.REJECT);
            request.setStatus(StatusEnum.REJECTED.name());
        }
        request.setAcceptedUserProfile(userProfile.getId());
        this.requestServiceImpl.saveRequest(request);
        requestService.addRequestCommentsToList(request,
                userProfile.getName() + " has " + (requestAcceptBean.isAccepted() ? "accepted" : "rejected")
                + " the request",
                new UserProfile(Constants.SYSTEM),
                (requestAcceptBean.isAccepted() ? StatusEnum.PROCESSING : StatusEnum.REJECTED).name());
        return request.getStatus();
    }

    /**
     * This method modifies request status
     *
     * @param requestAcceptBean
     * @param userProfile
     * @throws NoEntityFoundException
     */
    @Override
    public String closeRequest(String requestId, UserProfile userProfile)
            throws NoEntityFoundException, OperationNotAllowedException {
        Request request = this.requestServiceImpl.getRequestById(requestId);
        request.setStatus(StatusEnum.CLOSED.name());
        this.requestServiceImpl.throwIfOperationNotPermit(request, userProfile, OperationsEnum.CLOSE);
        this.requestServiceImpl.saveRequest(request);
        return request.getStatus();
    }

    /**
     * This method validated input entries and returns validated values
     */
    @Override
    public List<EntryBean> validateEntry(ConfigEntryInputBean ucsInputBean) throws NoEntityFoundException {
        String projectId = requestRepository.getProjectId(ucsInputBean.getRequestId());

        // entryList that has to be
        List<EntryBean> toBeValidatedEntryList = ucsInputBean.getEntryList();

        // entryList against which to validate
        List<EntryBean> entryConfigList = findEntryByProjectId(projectId);

        for (EntryBean entry : toBeValidatedEntryList) {
            String entryName = entry.getEntry();
            compare(entryName, entry, entryConfigList);
        }
        toBeValidatedEntryList.forEach((EntryBean entry) -> setWarningErrorMessage(entry));

        Request request = this.requestServiceImpl.getRequestById(ucsInputBean.getRequestId());
        Gson gson = new Gson();
        Object data = request.getServiceDetails().get(ServiceNameEnum.FUNCTIONAL_SECURITY_DATA.name()).getInput()
                .getData();
        String dataString = gson.toJson(data);
        ConfigDataBean configDataBean = gson.fromJson(dataString, ConfigDataBean.class);
        configDataBean.setEntryList(toBeValidatedEntryList);
        request.getServiceDetails().get(ServiceNameEnum.FUNCTIONAL_SECURITY_DATA.name()).getInput()
        .setData(configDataBean);
        this.requestServiceImpl.saveRequest(request);
        return this.configAcceptanceService.validate(ucsInputBean, toBeValidatedEntryList);
    }

    /**
     * set details for an individual entry
     */
    @Override
    public EntryBean getGroupAndCategory(String entryName, String projectId) throws NoEntityFoundException {
        EntryBean valueSetValidationEntryBean = new EntryBean();
        List<EntryBean> entryConfigList = findEntryByProjectId(projectId);
        compare(entryName, valueSetValidationEntryBean, entryConfigList);
        setWarningErrorMessage(valueSetValidationEntryBean);
        return valueSetValidationEntryBean;
    }

    /**
     * Entry values matched with entry value with regex
     *
     * @param entryName
     * @param valueSetValidationEntryBean
     * @param entryConfigList
     */
    private static void compare(String entryName, EntryBean valueSetValidationEntryBean,
            List<EntryBean> entryConfigList) {
        for (EntryBean entry : entryConfigList) {
            Pattern p = Pattern.compile(entry.getEntry());
            if (p.matcher(entryName).matches()) {
                setValue(valueSetValidationEntryBean, entry);
                break;
            } else {
                valueSetValidationEntryBean.setError("No match found");
            }
        }
    }


    /**
     * map for entries with parameters replaced
     */
    @Override
    public List<EntryBean> findEntryByProjectId(String projectId) {
        List<EntryBean> entryBean = entryBeanMap.get(projectId);
        if (entryBean == null) {
            entryBean = populateEntryBeanMap(projectId);
        }
        return entryBean;
    }

    @Override
    public List<EntryBean> populateEntryBeanMap(String projectId) {
        List<EntryBean> entryBean;
        List<EntryIBean> entryConfigList = configEntryRepository.findEntriesByProjectId(projectId);
        List<EntryBean> paramInfoEntryBeanList = new ArrayList<>();
        entryBean = populateValidationEntryBean(entryConfigList);
        List<ConfigEntryParam> configParamList = findConfigParamByProjectId(projectId);
        for (EntryBean entry : entryBean) {
            escapeSpecialChars(entry);
            String entryNameFromDb = entry.getEntry();
            entryNameFromDb = replaceRegex(configParamList, entryNameFromDb);
            entry.setEntry(entryNameFromDb);
            if (!StringUtils.isEmpty(entry.getHistoryCheck())) {
                entry.setHistoryCheck(replaceRegex(configParamList, entry.getHistoryCheck()));
            }
            if (!StringUtils.isEmpty(entry.getDisplay())) {
                entry.setDisplay(replaceRegex(configParamList, entry.getDisplay()));
            }
            populateParamInfoEntryBeanList(paramInfoEntryBeanList, entry);
        }
        if (!CollectionUtils.isEmpty(paramInfoEntryBeanList)) {
            entryBean.addAll(paramInfoEntryBeanList);
        }
        entryBeanMap.put(projectId, entryBean);
        return entryBean;
    }

    /**
     * To populate new EntryBeans with Param info in the paramInfoEntryBeanList
     *
     * @param paramInfoEntryBeanList
     * @param entry
     */
    private void populateParamInfoEntryBeanList(List<EntryBean> paramInfoEntryBeanList, EntryBean entry) {
        List<String> newEntries = createNewEntryNamesWithParameterInfo(entry);
        if (!CollectionUtils.isEmpty(newEntries)) {
            for (String string : newEntries) {
                EntryBean newEntry = new EntryBean();
                newEntry.setEntry(string);
                setValue(newEntry, entry);
                paramInfoEntryBeanList.add(newEntry);
            }
        }
    }

    /**
     * To create new Entry name with parameter info if it is present in the entry
     *
     * @param entry
     * @return
     */
    public List<String> createNewEntryNamesWithParameterInfo(EntryBean entry) {
        List<String> replacedEntryList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(entry.getParameterInfo())
                && !CollectionUtils.isEmpty(entry.getParameterInfo().get(0))) {
            configEntryService.populateParamInfoMap(entry.getParameterInfo().get(0), entry.getCategoryId());
            if (configEntryService.getParameterInfoAndCategoryIdMap().get(entry.getCategoryId()) != null) {
                configEntryService.getParameterInfoAndCategoryIdMap().get(entry.getCategoryId())
                .forEach((key, value) -> {
                    String replacedEntry = configEntryService
                            .replaceEntryDataWithParameterInfo(entry.getEntry(), key,
                                    value);
                            if (!StringUtils.isEmpty(replacedEntry)) {
                        replacedEntryList.add(replacedEntry);
                    }
                });
            }
        }
        return replacedEntryList;
    }

    /**
     * Replaces parameters with corresponding regex
     *
     * @param configParamList
     * @param entryNameFromDb
     * @return
     */
    private static String replaceRegex(List<ConfigEntryParam> configParamList, String entryNameFromDb) {
        if (entryNameFromDb.indexOf('<') != -1) {
            for (ConfigEntryParam configParameter : configParamList) {
                String replacableParam = configParameter.getRegex();
                if (entryNameFromDb.contains(configParameter.getParameter())) {
                    if (replacableParam.contains("<")) {
                        replacableParam = replaceRegex(configParamList, replacableParam);
                    }
                    entryNameFromDb = entryNameFromDb.replace(configParameter.getParameter(), replacableParam);
                }
            }
        }
        return entryNameFromDb;
    }

    /**
     * map for parameters and regex value
     */
    @Override
    public List<ConfigEntryParam> findConfigParamByProjectId(String projectId) {
        EntryParamsBean configEntryParam = configEntryParamMap.get(projectId);
        if (configEntryParam == null) {
            configEntryParam = populateConfigEntryParamMap(projectId);
        }
        return configEntryParam.getData();
    }

    /**
     * To populate config entry param map
     *
     * @param projectId
     * @return
     */
    @Override
    public EntryParamsBean populateConfigEntryParamMap(String projectId) {
        EntryParamsBean configEntryParam;
        configEntryParam = configEntryParamService.fetchConfigEntryParamsByProjectId(projectId);
        configEntryParamMap.put(projectId, configEntryParam);
        return configEntryParam;
    }

    /**
     * Populate ValidationEntryBean from ValidationEntryIBean
     *
     * @param entryListFromDb
     * @return
     */
    private static List<EntryBean> populateValidationEntryBean(List<EntryIBean> entryListFromDb) {
        List<EntryBean> valueSetEntryBean = new ArrayList<>();
        entryListFromDb.forEach((EntryIBean entry) -> {
            EntryBean entryBean = new EntryBean();
            entryBean.setEntry(entry.getEntry());
            entryBean.setCategoryName(entry.getCategoryName());
            entryBean.setGroupName(entry.getGroupName());
            entryBean.setCategoryId(entry.getCategoryId());
            entryBean.setGroupId(entry.getGroupId());
            entryBean.setEntryActive(entry.isEntryActive());
            entryBean.setEntryCritical(entry.isEntryCritical());
            entryBean.setCategoryActive(entry.isCategoryActive());
            entryBean.setGroupActive(entry.isGroupActive());
            entryBean.setHistoryCheck(entry.getHistoryCheck());
            entryBean.setParameterInfo(setParamInfoToBean(entry.getParameterInfo()));
            entryBean.setDisplay(entry.getDisplay());
            entryBean.setCategoryConfigurationBean(
                    getCategoryConfigurationBeanFromJson(entry.getCategoryConfiguration()));
            valueSetEntryBean.add(entryBean);
        });
        return valueSetEntryBean;
    }

    private static List<List<String>> setParamInfoToBean(String paramInfoFromDb) {
        List<List<String>> paramInfoList = new ArrayList<>();
        if (paramInfoFromDb != null && !paramInfoFromDb.isEmpty()) {
            Gson gson = new Gson();
            Type listOfList = new TypeToken<ArrayList<List<String>>>() {
            }.getType();
            paramInfoList = gson.fromJson(paramInfoFromDb, listOfList);
        }
        return paramInfoList;
    }

    /**
     * Populate CategoryConfigurationBean from json string
     *
     * @param jsonString
     * @return
     */
    private static CategoryConfigurationBean getCategoryConfigurationBeanFromJson(String jsonString) {
        if (jsonString != null && !jsonString.isEmpty()) {
            Gson gson = new Gson();
            Type listType = new TypeToken<CategoryConfigurationBean>() {
            }.getType();
            return gson.fromJson(jsonString, listType);
        }
        return new CategoryConfigurationBean();
    }

    /**
     * escapes special characters for regex pattern matching in entries
     *
     * @param entryBean
     * @return validationEntryBean
     */
    private static void escapeSpecialChars(EntryBean entryBean) {
        String entry = entryBean.getEntry();
        int indexStart = 0;
        while (indexStart < entry.length()) {
            int indexEnd = entry.indexOf('<', indexStart);
            if (indexEnd != -1) {
                if (indexStart != 0) {
                    entry = entry.substring(0, indexStart) + Pattern.quote(entry.substring(indexStart, indexEnd))
                    + entry.substring(indexEnd, entry.length());
                } else {
                    entry = entry.replace(entry.substring(indexStart, indexEnd),
                            Pattern.quote(entry.substring(indexStart, indexEnd)));
                }
            } else {
                if (indexStart == 0) {
                    entry = entry.replace(entry, Pattern.quote(entry));
                }
                break;
            }
            indexStart = (entry.indexOf('>', indexEnd)) + 1;
        }
        entryBean.setEntry(entry);
    }

    /**
     * sets warning and error message for entries
     *
     * @param entry
     */
    private void setWarningErrorMessage(EntryBean entry) {
        Map<Integer, String> testSystemMap = testSystemService.fetchActiveTestSystemAsMap();
        if (entry.getError() == null) {
            if (!entry.isEntryActive() || !entry.isCategoryActive() || !entry.isGroupActive()) {
                entry.setError("No Match found");
            } else {
                String warning = setWarning(entry, testSystemMap);
                entry.setWarning(warning);
            }
        }
    }

    /**
     * Sets warning message for an entry
     *
     * @param entry
     * @param testSystemMap
     * @return
     */
    private static String setWarning(EntryBean entry, Map<Integer, String> testSystemMap) {
        String warning = "";
        if (entry.getCategoryConfigurationBean().isRepushDaemon()) {
            warning += "Repush Demon ";
        }
        if (entry.getCategoryConfigurationBean().isWeeklyRefresh()) {
            warning += "Weekly Refresh ";
        }
        if (entry.getCategoryConfigurationBean().isSkipRefresh()) {
            String testSystemName = "";
            for (int i = 0; i < entry.getCategoryConfigurationBean().getTestSystemIds().size(); i++) {
                testSystemName += " "
                        + testSystemMap.get(entry.getCategoryConfigurationBean().getTestSystemIds().get(i));
            }
            warning += "Skip Refresh with" + testSystemName;
        }
        if (entry.isEntryCritical()) {
            warning += " Entry is critical";
        }
        return warning;
    }

    /**
     * populates entries from one to another
     *
     * @param entry
     * @param entryFromDb
     */
    private static void setValue(EntryBean entry, EntryBean entryFromDb) {
        entry.setGroupName(entryFromDb.getGroupName());
        entry.setGroupId(entryFromDb.getGroupId());
        entry.setCategoryName(entryFromDb.getCategoryName());
        entry.setCategoryId(entryFromDb.getCategoryId());
        entry.setGroupActive(entryFromDb.isGroupActive());
        entry.setCategoryActive(entryFromDb.isCategoryActive());
        entry.setEntryActive(entryFromDb.isEntryActive());
        entry.setEntryCritical(entryFromDb.isEntryCritical());
        entry.setEntryFallback(entryFromDb.getEntryFallback());
        entry.setHistoryCheck(entryFromDb.getHistoryCheck());
        entry.setDisplay(entryFromDb.getDisplay());
        entry.setError(null);
        entry.setCategoryConfigurationBean(entryFromDb.getCategoryConfigurationBean());
    }

    /**
     * Gets the search result for the entered params
     *
     * @param entry
     * @param groups
     * @param categories
     * @param phases
     * @param expiryStartDate
     * @param expiryEndDate
     * @param createdStartDate
     * @param createdEndDate
     * @param winAproachIds
     * @param executionStatus
     * @param archived
     * @return
     */
    @Override
    public List<SearchEntryBean> getFunctionalSecuritiesEntries(String entry, List<String> groups,
            List<String> categories,
            List<String> phases, Long expiryStartDate, Long expiryEndDate, Long createdStartDate, Long createdEndDate,
            List<String> winAproachIds, List<String> executionStatus, List<Integer> archived) {
        List<SearchEntryBean> searchEntryList = new ArrayList<>();
        StringBuilder searchDBQuery = new StringBuilder(entrySearchSql);
        if (!StringUtils.isEmpty(entry)) {
            searchDBQuery.append(" " + entryColumn + entry + "'");
        }
        if (!CollectionUtils.isEmpty(categories)) {
            searchDBQuery.append(" " + entryCategoryColumn + String.join("','", categories) + "')");
        }
        if (!CollectionUtils.isEmpty(groups)) {
            searchDBQuery.append(" " + entryGroup + String.join("','", groups) + "')");
        }
        if (!CollectionUtils.isEmpty(phases)) {
            searchDBQuery.append(" " + entryPhase + String.join("','", phases) + "')");
        }
        if (expiryStartDate == null) {
            expiryStartDate = System.currentTimeMillis();
        }
            searchDBQuery.append(
                    " " + entryExpiryDate + " >= "
                            + expiryStartDate);
        if (expiryEndDate == null) {
            expiryEndDate = System.currentTimeMillis() + Constants.CONVERT_MONTH_TO_MILISECONDS;
        }
        searchDBQuery.append(" " + entryExpiryDate + " <= " + expiryEndDate);
        if (createdStartDate == null) {
            createdStartDate = System.currentTimeMillis() - Constants.CONVERT_MONTH_TO_MILISECONDS * 3;
        }
        searchDBQuery.append(" " + entryCreateDate + " >= " + createdStartDate);
        if (createdEndDate == null) {
            createdEndDate = System.currentTimeMillis();
        }
        searchDBQuery.append(" " + entryCreateDate + " <= " + createdEndDate);
        if (!CollectionUtils.isEmpty(winAproachIds)) {
            searchDBQuery.append(" " + winApproachColumn + String.join(",", winAproachIds)
            + ")");
        }
        if (!CollectionUtils.isEmpty(executionStatus)) {
            searchDBQuery.append(" " + entryExecutionColumn + String.join("','", executionStatus) + "')");
        }
        if (!CollectionUtils.isEmpty(archived)) {
            searchDBQuery.append(" " + entryArchivedColumn + StringUtils.join(archived, ",") + ")");
        } else {
            searchDBQuery.append(" " + entryArchivedColumn + 0 + ")");
        }

        List<Map<String, Object>> resultsList = entityManagerServiceImpl.executeQuery(searchDBQuery.toString(), null,
                null);

        resultsList.forEach((Object entryArrayObj) -> {
            SearchEntryBean searchBean = new SearchEntryBean();
            Object[] entryArray = (Object[]) entryArrayObj;
            searchBean.setEntryDetailsId((Integer) entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_0]);
            searchBean.setEntry(entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_1].toString());
            searchBean.setGroup(entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_2].toString());
            if (entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_3] != null) {
                searchBean.setCategory(entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_3].toString());
            }
            searchBean.setPhase(entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_4].toString());
            if (entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_5] != null) {
                searchBean.setWinAproachId(entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_5].toString());
            }
            searchBean.setArchived(Boolean.TRUE.equals(entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_6]));
            if (entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_7] != null) {
                searchBean.setExpiryDate(new Long(entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_7].toString()));
            }
            if (entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_8] != null) {
                searchBean.setCreatedDate(new Long(entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_8].toString()));
            }
            searchBean.setExecutionStatus(entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_9].toString());
            if (entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_10] != null) {
                searchBean.setRequestId(entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_10].toString());
            }
            if (entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_11] != null) {
                searchBean.setProcessorId((Integer) entryArray[Constants.REFRENCE_DATA_ARRAY_INDEX_11]);
            }
            searchEntryList.add(searchBean);
        });
        return searchEntryList;
    }

    /**
     * This method creates a request based on the selected entries and will move the
     * request directly to processing state
     *
     * @param selectedSearchedEntriesBean
     * @param userProfile
     * @return String requestId
     * @throws NoEntityFoundException
     * @throws OperationNotAllowedException
     * @throws InvalidDataException
     * @throws UserNotFoundException
     * @throws InjectionException
     * @throws WinAproachRecordException
     */
    @Override
    public String createRequestFromEntries(SelectedSearchedEntriesBean selectedSearchedEntriesBean,
            UserProfile userProfile) throws NoEntityFoundException, UserNotFoundException,
    OperationNotAllowedException, InvalidDataException,
    WinAproachRecordException, InjectionException {
        RequestBean requestBean = prepareReqeustBeanFromEntries(selectedSearchedEntriesBean, userProfile);
        String requestId = requestService.createRequest(requestBean, userProfile);
        acceptRejectRequest(getRequestAcceptBean(requestId), userProfile);
        configEntryDetailsRepository.duplicateAndSaveEntries(requestId, selectedSearchedEntriesBean.getEntryList());
        return requestId;
    }

    /**
     * This method generates the requestBean object to pass in to create request
     * service method.
     *
     * @param selectedSearchedEntriesBean
     * @param userProfile
     * @return requestBean
     * @throws NoEntityFoundException
     * @throws UserNotFoundException
     */
    private RequestBean prepareReqeustBeanFromEntries(SelectedSearchedEntriesBean selectedSearchedEntriesBean,
            UserProfile userProfile) throws NoEntityFoundException, UserNotFoundException {
        RequestBean requestBean = new RequestBean();
        requestBean.setTitle(getTitleForCapRestoreRequest());
        requestBean.setProject(getProjectForCapRestoreRequest(userProfile));
        requestBean.setServices(getServiceDetailForCapRestoreRequest(selectedSearchedEntriesBean));
        requestBean.setActionToPerform(OperationsEnum.CREATE);
        requestBean.setTags(new ArrayList<>());
        return requestBean;
    }

    /**
     * This method generates the title for the restore execution request to be
     * created.
     *
     * @return String title
     */
    private String getTitleForCapRestoreRequest() {
        LocalDate currentDate = LocalDate.now();
        Month month = currentDate.getMonth();
        int year = currentDate.getYear();
        return "Restore Execution - " + month + " " + year;
    }

    /**
     * This method generates a RequestAcceptBean to mark request as accepted.
     *
     * @param requestId
     * @return RequestAcceptBean
     */
    private RequestAcceptBean getRequestAcceptBean(String requestId) {
        RequestAcceptBean requestAcceptBean = new RequestAcceptBean();
        requestAcceptBean.setAccepted(true);
        requestAcceptBean.setRequestId(requestId);
        return requestAcceptBean;
    }

    /**
     * This method generates the service_details for the requestBean.
     *
     * @return RequestServiceDetailsBean Map
     */
    private Map<String, RequestServiceDetailsBean> getServiceDetailForCapRestoreRequest(
            SelectedSearchedEntriesBean selectedSearchedEntriesBean) {
        RequestServiceDetailsBean serviceDetails = new RequestServiceDetailsBean();
        serviceDetails.setSubServices(Arrays.asList((ServiceNameEnum.FUNCTIONAL_SECURITY_DATA.name())));
        RequestDataInputBean requestDataInputBean = new RequestDataInputBean();
        requestDataInputBean.setType(Constants.CAP_RESTORE_DATA);
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("winaproachRecord", selectedSearchedEntriesBean.getWinaproachRecord());
        Gson gson = new Gson();
        JsonElement jsonElement = gson.toJsonTree(dataMap);
        requestDataInputBean.setData(gson.fromJson(jsonElement, Object.class));
        serviceDetails.setInput(requestDataInputBean);
        Map<String, RequestServiceDetailsBean> serviceDetailsMap = new HashMap<>();
        serviceDetailsMap.put(ServiceNameEnum.FUNCTIONAL_SECURITY_DATA.name(), serviceDetails);
        return serviceDetailsMap;
    }

    /**
     * This fetches the special Project info for the user.
     *
     * @param userProfile
     * @return ProjectBean
     * @throws NoEntityFoundException
     * @throws UserNotFoundException
     */
    private ProjectBean getProjectForCapRestoreRequest(UserProfile userProfile)
            throws NoEntityFoundException, UserNotFoundException {
        return projectService.fetchUserProjectsBySpecialServiceAndPhaseCategory(userProfile, true,
                ServiceNameEnum.FUNCTIONAL_SECURITY_DATA.name(), TestSystemCategoryEnum.ALL.name());
    }

}
