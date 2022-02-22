/**
 *
 */
package com.amadeus.mdi.service.configcatalogue;

import java.util.List;

import com.amadeus.mdi.beans.configcatalogue.ConfigEntryInputBean;
import com.amadeus.mdi.beans.configcatalogue.EntryBean;
import com.amadeus.mdi.beans.configcatalogue.EntryParamsBean;
import com.amadeus.mdi.beans.configcatalogue.RequestAcceptBean;
import com.amadeus.mdi.beans.configcatalogue.SearchEntryBean;
import com.amadeus.mdi.beans.configcatalogue.SelectedSearchedEntriesBean;
import com.amadeus.mdi.entity.UserProfile;
import com.amadeus.mdi.entity.configcatalogue.ConfigEntryParam;
import com.amadeus.mdi.exception.InjectionException;
import com.amadeus.mdi.exception.InvalidDataException;
import com.amadeus.mdi.exception.NoEntityFoundException;
import com.amadeus.mdi.exception.OperationNotAllowedException;
import com.amadeus.mdi.exception.UserNotFoundException;
import com.amadeus.mdi.exception.WinAproachRecordException;

/**
 * This interface holds all the services related to config Request
 *
 * @author sav
 *
 */
public interface ConfigRequestService {

    /**
     * This method modifies request status
     *
     * @param requestAcceptBean
     * @param userProfile
     * @throws NoEntityFoundException
     * @throws OperationNotAllowedException
     */
    String acceptRejectRequest(RequestAcceptBean requestAcceptBean, UserProfile userProfile)
            throws NoEntityFoundException, OperationNotAllowedException;

    /**
     * This method modifies request status
     *
     * @param requestId
     * @param userProfile
     * @throws NoEntityFoundException
     * @throws OperationNotAllowedException
     */
    String closeRequest(String requestId, UserProfile userProfile)
            throws NoEntityFoundException, OperationNotAllowedException;

    /**
     * This method validates entries
     *
     * @param ucsInputBean
     * @throws NoEntityFoundException
     */
    List<EntryBean> validateEntry(ConfigEntryInputBean ucsInputBean) throws NoEntityFoundException;

    /**
     * This method compares regex for entryName and returns corresponding group and
     * category
     *
     * @param entryName
     * @throws NoEntityFoundException
     */
    EntryBean getGroupAndCategory(String entryName, String projectId) throws NoEntityFoundException;

    List<EntryBean> findEntryByProjectId(String projectId);

    List<ConfigEntryParam> findConfigParamByProjectId(String projectId);

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
    List<SearchEntryBean> getFunctionalSecuritiesEntries(String entry, List<String> groups, List<String> categories,
            List<String> phases, Long expiryStartDate, Long expiryEndDate, Long createdStartDate, Long createdEndDate,
            List<String> winAproachIds, List<String> executionStatus, List<Integer> archived);

    /**
     * This method will create a request based on the selected entries and will move
     * the request directly to processing state
     *
     * @param selectedSearchedEntriesBean
     * @param userProfile
     * @return
     * @throws NoEntityFoundException
     * @throws OperationNotAllowedException
     * @throws InvalidDataException
     * @throws UserNotFoundException
     * @throws InjectionException
     * @throws WinAproachRecordException
     */
    String createRequestFromEntries(SelectedSearchedEntriesBean selectedSearchedEntriesBean, UserProfile userProfile)
            throws NoEntityFoundException, OperationNotAllowedException, InvalidDataException, UserNotFoundException,
            WinAproachRecordException, InjectionException;
    /**
     * To populate the entry bean map
     *
     * @param projectId
     * @return
     */
    List<EntryBean> populateEntryBeanMap(String projectId);

    /**
     * To populate config entry param map
     * @param projectId
     * @return
     */
    EntryParamsBean populateConfigEntryParamMap(String projectId);
}
