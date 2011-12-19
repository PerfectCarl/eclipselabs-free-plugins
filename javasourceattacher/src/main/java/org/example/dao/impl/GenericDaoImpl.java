package org.example.dao.impl;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.example.dao.GenericDao;

/**
 * A Generic DAO class.
 *
 * @param <T> entity type
 * @param <ID> primary key type
 */
public abstract class GenericDaoImpl <T, ID extends Serializable>
        implements GenericDao<T, ID> {

    private Class<T> persistentClass;

    private EntityManager entityManager;

    /**
     * Default constructor.
     */
    @SuppressWarnings("unchecked")
    public GenericDaoImpl() {
        this.persistentClass = (Class<T>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];
    }

    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}
    protected EntityManager getEntityManager() {
        return this.entityManager;
    }

    protected Class<T> getPersistentClass() {
        return this.persistentClass;
    }

    public T findById(ID id) {
        return entityManager.find(getPersistentClass(), id);
    }

    @SuppressWarnings("unchecked")
	public List<T> findAll() {
        List<T> results = entityManager.createQuery("select o from "
        		+ getPersistentClass().getName() + " o").getResultList();
        results.size(); // In JPA apps, we don't need this call!!
        return results;
    }

    public void persist(T object) {
        entityManager.persist(object);
    }

    public void flush() {
        entityManager.flush();
    }

    public void clear() {
        entityManager.clear();
    }

    public void remove(T object) {
    	if (!entityManager.contains(object)) {
    		// if object isn't managed by EM, load it into EM
    		object = entityManager.merge(object);
    	}
    	// object is now a managed object so it can be removed.
        entityManager.remove(object);
    }
}
