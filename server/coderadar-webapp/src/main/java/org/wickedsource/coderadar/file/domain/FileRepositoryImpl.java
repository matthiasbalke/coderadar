package org.wickedsource.coderadar.file.domain;


import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.Arrays;
import java.util.List;

public class FileRepositoryImpl implements FileRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<File> findInCommit(String commitName, List<String> filepaths) {
        // workaround to deal with databases that do not allow empty IN clauses
        if (filepaths == null || filepaths.isEmpty()) {
            filepaths = Arrays.asList("!impossible file path!");
        }
        Query query = entityManager.createQuery("select f from Commit c join c.files a join a.id.file f where c.name=:commitName and f.filepath in (:filepaths)");
        query.setParameter("commitName", commitName);
        query.setParameter("filepaths", filepaths);
        return query.getResultList();
    }
}
