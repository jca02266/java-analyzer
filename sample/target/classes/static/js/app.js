// User management utilities

function searchUsers(query) {
    fetch('/api/users/search?q=' + encodeURIComponent(query))
        .then(response => response.json())
        .then(data => {
            console.log('Search results:', data);
        })
        .catch(error => console.error('Error:', error));
}

function createUser(userData) {
    $.ajax({
        type: 'POST',
        url: '/api/users',
        contentType: 'application/json',
        data: JSON.stringify(userData),
        success: function(result) {
            console.log('User created:', result);
        },
        error: function(err) {
            console.error('Create failed:', err);
        }
    });
}

function updateUser(userId, userData) {
    axios.put('/api/users/' + userId, userData)
        .then(response => {
            console.log('User updated:', response.data);
        })
        .catch(error => console.error('Update failed:', error));
}

function deleteUser(userId) {
    axios.delete('/api/users/' + userId)
        .then(response => {
            console.log('User deleted');
        })
        .catch(error => console.error('Delete failed:', error));
}

function getOrders(userId) {
    fetch('/api/users/' + userId + '/orders')
        .then(response => response.json())
        .then(orders => {
            console.log('Orders for user ' + userId + ':', orders);
        });
}

function submitOrderForm(form) {
    const formData = new FormData(form);
    fetch('/api/orders', {
        method: 'POST',
        body: formData
    })
    .then(response => response.json())
    .then(result => console.log('Order submitted:', result));
}
