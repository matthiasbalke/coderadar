package org.wickedsource.coderadar.job.merge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.wickedsource.coderadar.analyzer.api.ChangeType;
import org.wickedsource.coderadar.commit.domain.Commit;
import org.wickedsource.coderadar.commit.domain.CommitRepository;
import org.wickedsource.coderadar.commit.domain.CommitToFileAssociation;
import org.wickedsource.coderadar.commit.domain.CommitToFileAssociationRepository;
import org.wickedsource.coderadar.commit.event.CommitToFileAssociatedEvent;
import org.wickedsource.coderadar.file.domain.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommitLogMerger {

    private Logger logger = LoggerFactory.getLogger(CommitLogMerger.class);

    private CommitLogEntryRepository commitLogEntryRepository;

    private FileRepository fileRepository;

    private CommitRepository commitRepository;

    private CommitToFileAssociationRepository commitToFileAssociationRepository;

    private ApplicationEventPublisher eventPublisher;

    private static List<ChangeType> ALL_BUT_DELETED = Arrays.asList(ChangeType.ADD, ChangeType.RENAME, ChangeType.COPY, ChangeType.MODIFY, ChangeType.UNCHANGED);

    @Autowired
    public CommitLogMerger(CommitLogEntryRepository commitLogEntryRepository, FileRepository fileRepository, CommitRepository commitRepository, CommitToFileAssociationRepository commitToFileAssociationRepository, ApplicationEventPublisher eventPublisher) {
        this.commitLogEntryRepository = commitLogEntryRepository;
        this.fileRepository = fileRepository;
        this.commitRepository = commitRepository;
        this.commitToFileAssociationRepository = commitToFileAssociationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mergeCommit(Commit commit) {
        checkEligibility(commit);
        logger.debug("starting merge of commit {}", commit);
        associateWithAddedAndCopiedFiles(commit);
        List<File> modifiedFiles = associateWithModifiedFiles(commit);
        List<File> renamedFiles = associateWithRenamedFiles(commit);
        associateWithUnchangedFiles(commit, modifiedFiles, renamedFiles);
        commit.setMerged(true);
        commitRepository.save(commit);
        // commit entity is updated in database implicitly
    }

    private List<File> associateWithRenamedFiles(Commit commit) {
        List<CommitLogEntry> renamedFiles = commitLogEntryRepository.findByCommitNameAndChangeTypeIn(commit.getName(), Collections.singletonList(ChangeType.RENAME));

        Map<String, CommitLogEntry> oldPathToFile = new HashMap<>();
        for (CommitLogEntry file : renamedFiles) {
            oldPathToFile.put(file.getOldFilepath(), file);
        }

        logger.debug("found {} RENAMED files", renamedFiles.size());
        List<String> oldFilepaths = renamedFiles
                .stream()
                .map(CommitLogEntry::getOldFilepath)
                .collect(Collectors.toList());
        List<File> filesToAssociate = fileRepository.findInCommit(commit.getParentCommitName(), oldFilepaths);
        for (File file : filesToAssociate) {
            File newFile = new File();
            newFile.setIdentity(file.getIdentity());
            newFile.setFilepath(oldPathToFile.get(file.getFilepath()).getFilepath());
            fileRepository.save(newFile);
            CommitToFileAssociation association = new CommitToFileAssociation(commit, newFile, ChangeType.RENAME);
            commit.getFiles().add(association);
            saveCommitToFileAssociation(association);
        }
        return filesToAssociate;
    }

    private List<File> associateWithModifiedFiles(Commit commit) {
        List<CommitLogEntry> modifiedFiles = commitLogEntryRepository.findByCommitNameAndChangeTypeIn(commit.getName(), Collections.singletonList(ChangeType.MODIFY));
        logger.debug("found {} MODIFIED files", modifiedFiles.size());
        List<String> filepaths = modifiedFiles
                .stream()
                .map(CommitLogEntry::getFilepath)
                .collect(Collectors.toList());
        List<File> filesToAssociate = fileRepository.findInCommit(commit.getParentCommitName(), filepaths);
        for (File file : filesToAssociate) {
            CommitToFileAssociation association = new CommitToFileAssociation(commit, file, ChangeType.MODIFY);
            commit.getFiles().add(association);
            saveCommitToFileAssociation(association);
        }
        return filesToAssociate;
    }

    private void associateWithAddedAndCopiedFiles(Commit commit) {
        List<CommitLogEntry> addedFiles = commitLogEntryRepository.findByCommitNameAndChangeTypeIn(commit.getName(), Arrays.asList(ChangeType.ADD, ChangeType.COPY));
        logger.debug("found {} ADDED and COPIED files", addedFiles.size());
        for (CommitLogEntry addedFile : addedFiles) {
            File file = new File();
            file.setFilepath(addedFile.getFilepath());
            fileRepository.save(file);
            file.setIdentity(new FileIdentity());
            CommitToFileAssociation association = new CommitToFileAssociation(commit, file, addedFile.getChangeType());
            if (!commit.getFiles().contains(association)) {
                commit.getFiles().add(association);
                saveCommitToFileAssociation(association);
            }
        }
    }

    private void associateWithUnchangedFiles(Commit commit, List<File> modifiedFiles, List<File> renamedFiles) {
        List<File> unchangedFiles = fileRepository.findInCommit(ALL_BUT_DELETED, commit.getParentCommitName(), commit.getProject().getId());
        unchangedFiles.removeAll(modifiedFiles);
        unchangedFiles.removeAll(renamedFiles);
        logger.debug("found {} UNCHANGED files", unchangedFiles.size());
        for (File file : unchangedFiles) {
            CommitToFileAssociation association = new CommitToFileAssociation(commit, file, ChangeType.UNCHANGED);
            if (!commit.getFiles().contains(association)) {
                commit.getFiles().add(association);
                saveCommitToFileAssociation(association);
            }
        }
    }

    private void saveCommitToFileAssociation(CommitToFileAssociation association) {
        eventPublisher.publishEvent(new CommitToFileAssociatedEvent(association));
        commitToFileAssociationRepository.save(association);
    }

    private void checkEligibility(Commit commit) {
        if (commit.isMerged()) {
            throw new IllegalStateException(String.format("commit is already merged: %s", commit));
        }
        if (!commit.isScanned()) {
            throw new IllegalStateException(String.format("commit is not yet scanned: %s", commit));
        }
    }

}
