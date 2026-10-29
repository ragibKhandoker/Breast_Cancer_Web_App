package com.cancerweb.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.ContactRequest;
import com.cancerweb.backend.exception.MessageNotFoundException;
import com.cancerweb.backend.model.ContactMessage;
import com.cancerweb.backend.model.MessageStatus;
import com.cancerweb.backend.repository.ContactMessageRepository;

@Service
public class ContactService {

    /** The inbox shows the newest messages; older ones stay in the database. */
    private static final int INBOX_LIMIT = 500;

    private final ContactMessageRepository repository;
    private final MongoTemplate mongo;

    public ContactService(ContactMessageRepository repository, MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    /** Stores a validated Contact form submission. */
    public ContactMessage submit(ContactRequest request) {
        ContactMessage message = new ContactMessage(null, request.name().strip(),
                request.email().strip().toLowerCase(Locale.ROOT), request.subject().strip(),
                request.message().strip(), Instant.now(), MessageStatus.NEW);
        return repository.save(message);
    }

    /** Administrator's inbox, newest first; {@code status} null means all messages. */
    public List<ContactMessage> list(MessageStatus status) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(INBOX_LIMIT);
        if (status == MessageStatus.READ) {
            query.addCriteria(Criteria.where("status").is(MessageStatus.READ));
        } else if (status == MessageStatus.NEW) {
            // Messages from before the status field existed count as new.
            query.addCriteria(Criteria.where("status").ne(MessageStatus.READ));
        }
        return mongo.find(query, ContactMessage.class);
    }

    public ContactMessage setStatus(String id, MessageStatus status) {
        if (mongo.updateFirst(byId(id), new Update().set("status", status), ContactMessage.class).getMatchedCount() == 0) {
            throw new MessageNotFoundException();
        }
        return repository.findById(id).orElseThrow(MessageNotFoundException::new);
    }

    public void delete(String id) {
        if (mongo.remove(byId(id), ContactMessage.class).getDeletedCount() == 0) {
            throw new MessageNotFoundException();
        }
    }

    private static Query byId(String id) {
        return Query.query(Criteria.where("id").is(id));
    }
}
